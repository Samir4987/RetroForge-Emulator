#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "RetroForgeLibretro"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
using retro_environment_t = bool (*)(unsigned, void*);
using retro_video_refresh_t = void (*)(const void*, unsigned, unsigned, size_t);
using retro_audio_sample_t = void (*)(int16_t, int16_t);
using retro_audio_sample_batch_t = size_t (*)(const int16_t*, size_t);
using retro_input_poll_t = void (*)();
using retro_input_state_t = int16_t (*)(unsigned, unsigned, unsigned, unsigned);

struct GameInfo { const char* path; const void* data; size_t size; const char* meta; };
struct SystemInfo { const char* name; const char* version; const char* extensions; bool need_fullpath; bool block_extract; };
struct AvInfo { unsigned base_width, base_height, max_width, max_height; float aspect_ratio; double fps, sample_rate; };
struct PixelFormat { unsigned format; };

constexpr size_t kMaxAudioSamples = 32768;
constexpr unsigned kJoypadDevice = 1;
constexpr unsigned kAnalogDevice = 5;
constexpr unsigned kPointerDevice = 6;
constexpr unsigned kMaxPorts = 2;
constexpr unsigned kJoypadButtons = 16;

void* core = nullptr;
bool initialized = false;
bool game_loaded = false;
std::vector<uint8_t> rom_data;

std::mutex media_mutex;
std::vector<uint8_t> video_frame;
unsigned video_width = 0;
unsigned video_height = 0;
size_t video_pitch = 0;
unsigned video_pixel_format = 0; // Libretro 0RGB1555 until the core selects another format.
bool video_frame_ready = false;
std::vector<int16_t> audio_samples;
std::array<std::array<int16_t, kJoypadButtons>, kMaxPorts> joypad_state{};
std::array<std::array<int16_t, 2>, kMaxPorts> analog_state{};
int16_t pointer_x = 0;
int16_t pointer_y = 0;
bool pointer_pressed = false;

using ApiVersion = unsigned (*)();
using SetEnvironment = void (*)(retro_environment_t);
using SetVideo = void (*)(retro_video_refresh_t);
using SetAudio = void (*)(retro_audio_sample_t);
using SetAudioBatch = void (*)(retro_audio_sample_batch_t);
using SetInputPoll = void (*)(retro_input_poll_t);
using SetInputState = void (*)(retro_input_state_t);
using VoidFn = void (*)();
using GetSystemInfo = void (*)(SystemInfo*);
using LoadGame = bool (*)(const GameInfo*);
using RunFrame = void (*)();
using UnloadGame = void (*)();
using GetAvInfo = void (*)(AvInfo*);
ApiVersion api_version = nullptr;
SetEnvironment set_environment = nullptr;
SetVideo set_video = nullptr;
SetAudio set_audio = nullptr;
SetAudioBatch set_audio_batch = nullptr;
SetInputPoll set_input_poll = nullptr;
SetInputState set_input_state = nullptr;
VoidFn core_init = nullptr, core_deinit = nullptr;
GetSystemInfo get_system_info = nullptr;
LoadGame load_game = nullptr;
RunFrame run_frame = nullptr;
UnloadGame unload_game = nullptr;
GetAvInfo get_av_info = nullptr;

bool environment(unsigned command, void* data) {
    // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10.
    if (command == 10 && data) {
        const auto format = *static_cast<PixelFormat*>(data);
        if (format.format > 2) return false;
        std::lock_guard<std::mutex> lock(media_mutex);
        video_pixel_format = format.format;
        return true;
    }
    return false;
}

void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0 || pitch == 0 ||
        height > std::numeric_limits<size_t>::max() / pitch) return;
    const size_t bytes = pitch * static_cast<size_t>(height);
    std::lock_guard<std::mutex> lock(media_mutex);
    video_frame.resize(bytes);
    std::memcpy(video_frame.data(), data, bytes);
    video_width = width;
    video_height = height;
    video_pitch = pitch;
    video_frame_ready = true;
}

void audio_sample(int16_t left, int16_t right) {
    std::lock_guard<std::mutex> lock(media_mutex);
    if (audio_samples.size() >= kMaxAudioSamples) {
        audio_samples.erase(audio_samples.begin(), audio_samples.begin() + 2);
    }
    audio_samples.push_back(left);
    audio_samples.push_back(right);
}

size_t audio_sample_batch(const int16_t* data, size_t frames) {
    if (!data || frames == 0) return 0;
    std::lock_guard<std::mutex> lock(media_mutex);
    const size_t incoming = frames * 2;
    if (incoming >= kMaxAudioSamples) {
        audio_samples.assign(data + incoming - kMaxAudioSamples, data + incoming);
        return frames;
    }
    if (audio_samples.size() + incoming > kMaxAudioSamples) {
        const size_t remove = audio_samples.size() + incoming - kMaxAudioSamples;
        audio_samples.erase(audio_samples.begin(), audio_samples.begin() + remove);
    }
    audio_samples.insert(audio_samples.end(), data, data + incoming);
    return frames;
}

void input_poll() {}

int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= kMaxPorts) return 0;
    if (device == kJoypadDevice && id < kJoypadButtons) {
        std::lock_guard<std::mutex> lock(media_mutex);
        return joypad_state[port][id];
    }
    if (device == kAnalogDevice && index < 2 && id < 2) {
        std::lock_guard<std::mutex> lock(media_mutex);
        return analog_state[port][index];
    }
    if (device == kPointerDevice && port == 0) {
        std::lock_guard<std::mutex> lock(media_mutex);
        if (id == 0) return pointer_x;
        if (id == 1) return pointer_y;
        if (id == 2) return pointer_pressed ? 1 : 0;
    }
    return 0;
}

template <typename T> bool symbol(T& out, const char* name) {
    out = reinterpret_cast<T>(dlsym(core, name));
    if (!out) LOGE("Missing Libretro symbol: %s", name);
    return out != nullptr;
}

void reset_symbols() {
    api_version = nullptr; set_environment = nullptr; set_video = nullptr;
    set_audio = nullptr; set_audio_batch = nullptr; set_input_poll = nullptr;
    set_input_state = nullptr; core_init = nullptr; core_deinit = nullptr;
    get_system_info = nullptr; load_game = nullptr; run_frame = nullptr;
    unload_game = nullptr; get_av_info = nullptr;
}

void reset_frontend_state() {
    std::lock_guard<std::mutex> lock(media_mutex);
    video_frame.clear();
    video_width = video_height = 0;
    video_pitch = 0;
    video_pixel_format = 0;
    video_frame_ready = false;
    audio_samples.clear();
    for (auto& port : joypad_state) port.fill(0);
    for (auto& port : analog_state) port.fill(0);
    pointer_x = pointer_y = 0;
    pointer_pressed = false;
}

void close_core() {
    if (game_loaded && unload_game) unload_game();
    game_loaded = false;
    if (initialized && core_deinit) core_deinit();
    initialized = false;
    if (core) dlclose(core);
    core = nullptr;
    rom_data.clear();
    reset_symbols();
    reset_frontend_state();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_LibretroNative_nativeOpen(JNIEnv* env, jobject, jstring core_path, jstring rom_path) {
    close_core();
    if (!core_path || !rom_path) return env->NewStringUTF("Core path and ROM path are required.");

    const char* native_core_path = env->GetStringUTFChars(core_path, nullptr);
    if (!native_core_path) return env->NewStringUTF("Could not read core path.");
    const char* native_rom_path = env->GetStringUTFChars(rom_path, nullptr);
    if (!native_rom_path) {
        env->ReleaseStringUTFChars(core_path, native_core_path);
        return env->NewStringUTF("Could not read ROM path.");
    }

    core = dlopen(native_core_path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(core_path, native_core_path);
    if (!core) {
        const char* error = dlerror();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF(error ? error : "Could not load core library.");
    }

    bool ok =
        symbol(api_version, "retro_api_version") &&
        symbol(set_environment, "retro_set_environment") &&
        symbol(set_video, "retro_set_video_refresh") &&
        symbol(set_audio, "retro_set_audio_sample") &&
        symbol(set_audio_batch, "retro_set_audio_sample_batch") &&
        symbol(set_input_poll, "retro_set_input_poll") &&
        symbol(set_input_state, "retro_set_input_state") &&
        symbol(core_init, "retro_init") &&
        symbol(core_deinit, "retro_deinit") &&
        symbol(get_system_info, "retro_get_system_info") &&
        symbol(load_game, "retro_load_game") &&
        symbol(run_frame, "retro_run") &&
        symbol(unload_game, "retro_unload_game") &&
        symbol(get_av_info, "retro_get_system_av_info");
    if (!ok) {
        close_core();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF("Core is missing required Libretro API symbols.");
    }
    if (api_version() != 1U) {
        close_core();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF("Unsupported Libretro API version; expected version 1.");
    }

    set_environment(environment);
    set_video(video_refresh);
    set_audio(audio_sample);
    set_audio_batch(audio_sample_batch);
    set_input_poll(input_poll);
    set_input_state(input_state);
    core_init();
    initialized = true;

    SystemInfo info{};
    get_system_info(&info);
    if (!info.name || !info.extensions) {
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        close_core();
        return env->NewStringUTF("Core returned incomplete system information.");
    }
    GameInfo game{native_rom_path, nullptr, 0, nullptr};
    if (!info.need_fullpath) {
        std::ifstream input(native_rom_path, std::ios::binary | std::ios::ate);
        if (!input) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core();
            return env->NewStringUTF("Could not open ROM file.");
        }
        const std::streamoff length = input.tellg();
        if (length <= 0 || static_cast<unsigned long long>(length) >
                static_cast<unsigned long long>(std::numeric_limits<size_t>::max())) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core();
            return env->NewStringUTF("ROM file is empty or too large.");
        }
        rom_data.resize(static_cast<size_t>(length));
        input.seekg(0, std::ios::beg);
        if (!input.read(reinterpret_cast<char*>(rom_data.data()), length)) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core();
            return env->NewStringUTF("Could not read complete ROM file.");
        }
        game.data = rom_data.data();
        game.size = rom_data.size();
    }

    const bool loaded = load_game(&game);
    env->ReleaseStringUTFChars(rom_path, native_rom_path);
    if (!loaded) {
        close_core();
        return env->NewStringUTF("Libretro core rejected the ROM.");
    }
    game_loaded = true;
    AvInfo av_info{};
    get_av_info(&av_info);
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeRunFrame(JNIEnv*, jobject) {
    if (!initialized || !game_loaded || !run_frame) return JNI_FALSE;
    run_frame();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_emulator_LibretroNative_nativeGetVideoInfo(JNIEnv* env, jobject) {
    jint values[4];
    {
        std::lock_guard<std::mutex> lock(media_mutex);
        values[0] = static_cast<jint>(video_width);
        values[1] = static_cast<jint>(video_height);
        values[2] = static_cast<jint>(video_pitch);
        values[3] = static_cast<jint>(video_pixel_format);
    }
    jintArray result = env->NewIntArray(4);
    if (result) env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_LibretroNative_nativeCopyVideoFrame(JNIEnv* env, jobject, jobject destination) {
    if (!destination) return 0;
    auto* output = static_cast<uint8_t*>(env->GetDirectBufferAddress(destination));
    const jlong capacity = env->GetDirectBufferCapacity(destination);
    if (!output || capacity <= 0) return 0;
    std::lock_guard<std::mutex> lock(media_mutex);
    if (!video_frame_ready || video_frame.size() > static_cast<size_t>(capacity)) return 0;
    std::memcpy(output, video_frame.data(), video_frame.size());
    return static_cast<jint>(video_frame.size());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_LibretroNative_nativeCopyAudioSamples(JNIEnv* env, jobject, jshortArray destination) {
    if (!destination) return 0;
    const jsize capacity = env->GetArrayLength(destination);
    std::lock_guard<std::mutex> lock(media_mutex);
    const size_t count = std::min(audio_samples.size(), static_cast<size_t>(capacity));
    if (count == 0) return 0;
    env->SetShortArrayRegion(destination, 0, static_cast<jsize>(count), audio_samples.data());
    audio_samples.erase(audio_samples.begin(), audio_samples.begin() + count);
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetJoypadButton(JNIEnv*, jobject, jint port, jint button, jboolean pressed) {
    if (port < 0 || port >= static_cast<jint>(kMaxPorts) ||
        button < 0 || button >= static_cast<jint>(kJoypadButtons)) return;
    std::lock_guard<std::mutex> lock(media_mutex);
    joypad_state[static_cast<size_t>(port)][static_cast<size_t>(button)] = pressed ? 1 : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetAnalogAxis(JNIEnv*, jobject, jint port, jint stick, jint axis, jint value) {
    if (port < 0 || port >= static_cast<jint>(kMaxPorts) ||
        stick < 0 || stick >= 2 || axis < 0 || axis >= 2) return;
    std::lock_guard<std::mutex> lock(media_mutex);
    analog_state[static_cast<size_t>(port)][static_cast<size_t>(stick * 1 + axis)] =
        static_cast<int16_t>(std::max(-32768, std::min(32767, value)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetPointer(JNIEnv*, jobject, jint x, jint y, jboolean pressed) {
    std::lock_guard<std::mutex> lock(media_mutex);
    pointer_x = static_cast<int16_t>(std::max(-32768, std::min(32767, x)));
    pointer_y = static_cast<int16_t>(std::max(-32768, std::min(32767, y)));
    pointer_pressed = pressed == JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeClose(JNIEnv*, jobject) {
    close_core();
}
