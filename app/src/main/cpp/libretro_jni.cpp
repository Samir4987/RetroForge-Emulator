#include <jni.h>
#include <algorithm>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <array>
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

void* core = nullptr;
bool initialized = false;
bool game_loaded = false;
std::vector<uint8_t> rom_data;
std::vector<jint> video_pixels;
unsigned video_width = 0;
unsigned video_height = 0;
unsigned pixel_format = 0;
bool video_frame_ready = false;
std::array<bool, 16> joypad_buttons{};
int16_t pointer_x = 0;
int16_t pointer_y = 0;
bool pointer_pressed = false;
constexpr size_t AUDIO_RING_CAPACITY_FRAMES = 65536;
std::array<int16_t, AUDIO_RING_CAPACITY_FRAMES * 2U> audio_ring{};
size_t audio_read_frame = 0;
size_t audio_write_frame = 0;
size_t audio_queued_frames = 0;
std::mutex audio_mutex;
double core_sample_rate = 48000.0;
std::string system_directory;
std::string save_directory;
constexpr unsigned MAX_VIDEO_DIMENSION = 4096;
constexpr size_t MAX_VIDEO_PIXELS = 4U * 1024U * 1024U;
constexpr size_t MAX_ROM_BYTES = 512U * 1024U * 1024U;

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
using GetAvInfo = void (*)(AvInfo*);\nusing SerializeSize = size_t (*)();\nusing Serialize = bool (*)(void*, size_t);\nusing Unserialize = bool (*)(const void*, size_t);
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
GetAvInfo get_av_info = nullptr;\nSerializeSize serialize_size = nullptr;\nSerialize serialize_state = nullptr;\nUnserialize unserialize_state = nullptr;

bool environment(unsigned command, void* data) {
    // RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY = 9.
    if (command == 9 && data) {
        *static_cast<const char**>(data) = system_directory.c_str();
        return true;
    }
    // RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY = 31.
    if (command == 31 && data) {
        *static_cast<const char**>(data) = save_directory.c_str();
        return true;
    }
    // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10.
    if (command == 10 && data) {
        const auto format = *static_cast<PixelFormat*>(data);
        if (format.format > 2) return false;
        pixel_format = format.format;
        return true;
    }
    return false;
}

// Convert the core's current pixel format into tightly packed Android ARGB_8888.
void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    // Libretro permits null data to mean "reuse the previous frame".
    if (!data) return;
    if (width == 0 || height == 0 || width > MAX_VIDEO_DIMENSION ||
        height > MAX_VIDEO_DIMENSION ||
        static_cast<size_t>(width) * static_cast<size_t>(height) > MAX_VIDEO_PIXELS) {
        LOGE("Ignoring invalid video frame dimensions: %u x %u", width, height);
        return;
    }

    const size_t bytes_per_pixel = pixel_format == 1 ? 4U : 2U;
    if (pitch < static_cast<size_t>(width) * bytes_per_pixel) {
        LOGE("Ignoring video frame with invalid pitch.");
        return;
    }

    try {
        video_pixels.resize(static_cast<size_t>(width) * height);
    } catch (...) {
        LOGE("Could not allocate native video frame buffer.");
        return;
    }

    const auto* source = static_cast<const uint8_t*>(data);
    for (unsigned y = 0; y < height; ++y) {
        const uint8_t* row = source + static_cast<size_t>(y) * pitch;
        for (unsigned x = 0; x < width; ++x) {
            uint8_t red, green, blue;
            if (pixel_format == 1) {
                uint32_t value = 0;
                std::memcpy(&value, row + static_cast<size_t>(x) * 4U, sizeof(value));
                red = static_cast<uint8_t>((value >> 16U) & 0xFFU);
                green = static_cast<uint8_t>((value >> 8U) & 0xFFU);
                blue = static_cast<uint8_t>(value & 0xFFU);
            } else {
                uint16_t value = 0;
                std::memcpy(&value, row + static_cast<size_t>(x) * 2U, sizeof(value));
                if (pixel_format == 2) { // RGB565
                    red = static_cast<uint8_t>(((value >> 11U) & 0x1FU) * 255U / 31U);
                    green = static_cast<uint8_t>(((value >> 5U) & 0x3FU) * 255U / 63U);
                    blue = static_cast<uint8_t>((value & 0x1FU) * 255U / 31U);
                } else { // 0RGB1555
                    red = static_cast<uint8_t>(((value >> 10U) & 0x1FU) * 255U / 31U);
                    green = static_cast<uint8_t>(((value >> 5U) & 0x1FU) * 255U / 31U);
                    blue = static_cast<uint8_t>((value & 0x1FU) * 255U / 31U);
                }
            }
            video_pixels[static_cast<size_t>(y) * width + x] =
                static_cast<jint>(0xFF000000U |
                    (static_cast<uint32_t>(red) << 16U) |
                    (static_cast<uint32_t>(green) << 8U) |
                    static_cast<uint32_t>(blue));
        }
    }
    video_width = width;
    video_height = height;
    video_frame_ready = true;
}
void queue_audio_frame_unlocked(int16_t left, int16_t right) {
    if (audio_queued_frames == AUDIO_RING_CAPACITY_FRAMES) {
        audio_read_frame = (audio_read_frame + 1U) % AUDIO_RING_CAPACITY_FRAMES;
        --audio_queued_frames;
    }
    const size_t offset = audio_write_frame * 2U;
    audio_ring[offset] = left;
    audio_ring[offset + 1U] = right;
    audio_write_frame = (audio_write_frame + 1U) % AUDIO_RING_CAPACITY_FRAMES;
    ++audio_queued_frames;
}

void audio_sample(int16_t left, int16_t right) {
    std::lock_guard<std::mutex> lock(audio_mutex);
    queue_audio_frame_unlocked(left, right);
}

size_t audio_sample_batch(const int16_t* data, size_t frames) {
    if (!data) return 0;
    std::lock_guard<std::mutex> lock(audio_mutex);
    for (size_t frame = 0; frame < frames; ++frame) {
        queue_audio_frame_unlocked(data[frame * 2U], data[frame * 2U + 1U]);
    }
    return frames;
}
void input_poll() {}
int16_t input_state(unsigned port, unsigned device, unsigned, unsigned id) {
    if (port != 0) return 0;
    // RETRO_DEVICE_JOYPAD = 1; IDs 0..15 are the standard Libretro buttons.
    if (device == 1 && id < joypad_buttons.size()) {
        return joypad_buttons[id] ? 1 : 0;
    }
    // RETRO_DEVICE_POINTER = 6; X, Y and PRESSED are IDs 0, 1 and 2.
    if (device == 6) {
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
    set_environment = nullptr;
    set_video = nullptr;
    set_audio = nullptr;
    set_audio_batch = nullptr;
    set_input_poll = nullptr;
    set_input_state = nullptr;
    core_init = nullptr;
    core_deinit = nullptr;
    get_system_info = nullptr;
    load_game = nullptr;
    run_frame = nullptr;
    unload_game = nullptr;
    get_av_info = nullptr;
}

void close_core() {
    if (game_loaded && unload_game) unload_game();
    game_loaded = false;
    if (initialized && core_deinit) core_deinit();
    initialized = false;
    if (core) dlclose(core);
    core = nullptr;
    rom_data.clear();
    video_pixels.clear();
    video_width = 0;
    video_height = 0;
    pixel_format = 0;
    video_frame_ready = false;
    joypad_buttons.fill(false);
    pointer_x = 0;
    pointer_y = 0;
    pointer_pressed = false;
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        audio_read_frame = 0;
        audio_write_frame = 0;
        audio_queued_frames = 0;
    }
    core_sample_rate = 48000.0;
    system_directory.clear();
    save_directory.clear();
    reset_symbols();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_LibretroNative_nativeOpen(
    JNIEnv* env, jobject, jstring core_path, jstring rom_path,
    jstring system_path, jstring save_path
) {
    close_core();
    if (!core_path || !rom_path || !system_path || !save_path) {
        return env->NewStringUTF("Core, ROM, system and save paths are required.");
    }

    const char* native_core_path = env->GetStringUTFChars(core_path, nullptr);
    if (!native_core_path) return env->NewStringUTF("Could not read core path.");
    const char* native_rom_path = env->GetStringUTFChars(rom_path, nullptr);
    if (!native_rom_path) {
        env->ReleaseStringUTFChars(core_path, native_core_path);
        return env->NewStringUTF("Could not read ROM path.");
    }
    const char* native_system_path = env->GetStringUTFChars(system_path, nullptr);
    if (!native_system_path) {
        env->ReleaseStringUTFChars(core_path, native_core_path);
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF("Could not read system directory.");
    }
    const char* native_save_path = env->GetStringUTFChars(save_path, nullptr);
    if (!native_save_path) {
        env->ReleaseStringUTFChars(core_path, native_core_path);
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        env->ReleaseStringUTFChars(system_path, native_system_path);
        return env->NewStringUTF("Could not read save directory.");
    }
    system_directory = native_system_path;
    save_directory = native_save_path;
    env->ReleaseStringUTFChars(system_path, native_system_path);
    env->ReleaseStringUTFChars(save_path, native_save_path);

    core = dlopen(native_core_path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(core_path, native_core_path);
    if (!core) {
        const char* error = dlerror();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF(error ? error : "Could not load core library.");
    }

    bool ok =
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
    // Serialization is optional in Libretro; the runtime reports unsupported when absent.
    symbol(serialize_size, "retro_serialize_size");
    symbol(serialize_state, "retro_serialize");
    symbol(unserialize_state, "retro_unserialize");
    if (!ok) {
        close_core();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF("Core is missing required Libretro API symbols.");
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
                static_cast<unsigned long long>(MAX_ROM_BYTES)) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core();
            return env->NewStringUTF("ROM file is empty or exceeds the 512 MiB safety limit.");
        }
        try {
            rom_data.resize(static_cast<size_t>(length));
        } catch (...) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core();
            return env->NewStringUTF("Not enough memory to load this ROM.");
        }
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
    if (av_info.sample_rate >= 8000.0 && av_info.sample_rate <= 192000.0) {
        core_sample_rate = av_info.sample_rate;
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeRunFrame(JNIEnv*, jobject) {
    if (!initialized || !game_loaded || !run_frame) return JNI_FALSE;
    run_frame();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetJoypadButton(
    JNIEnv*, jobject, jint button_id, jboolean pressed
) {
    if (button_id >= 0 && static_cast<size_t>(button_id) < joypad_buttons.size()) {
        joypad_buttons[static_cast<size_t>(button_id)] = pressed == JNI_TRUE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetPointer(
    JNIEnv*, jobject, jint x, jint y, jboolean pressed
) {
    pointer_x = static_cast<int16_t>(std::max(-32768, std::min(32767, x)));
    pointer_y = static_cast<int16_t>(std::max(-32768, std::min(32767, y)));
    pointer_pressed = pressed == JNI_TRUE;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_example_emulator_LibretroNative_nativeGetAudioSampleRate(JNIEnv*, jobject) {
    return core_sample_rate;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_example_emulator_LibretroNative_nativeReadAudioSamples(
    JNIEnv* env, jobject, jint max_frames
) {
    if (max_frames <= 0) return nullptr;
    const size_t requested = static_cast<size_t>(max_frames);
    std::vector<jshort> samples;
    size_t frames_to_read = 0;
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        frames_to_read = std::min(requested, audio_queued_frames);
        if (frames_to_read == 0) return nullptr;
        try {
            samples.resize(frames_to_read * 2U);
        } catch (...) {
            return nullptr;
        }
        for (size_t frame = 0; frame < frames_to_read; ++frame) {
            const size_t offset = audio_read_frame * 2U;
            samples[frame * 2U] = audio_ring[offset];
            samples[frame * 2U + 1U] = audio_ring[offset + 1U];
            audio_read_frame = (audio_read_frame + 1U) % AUDIO_RING_CAPACITY_FRAMES;
        }
        audio_queued_frames -= frames_to_read;
    }

    jshortArray result = env->NewShortArray(static_cast<jsize>(samples.size()));
    if (!result) return nullptr;
    env->SetShortArrayRegion(result, 0, static_cast<jsize>(samples.size()), samples.data());
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_LibretroNative_nativeCopyVideoFrame(
    JNIEnv* env, jobject, jintArray destination
) {
    if (!video_frame_ready || video_width == 0 || video_height == 0) return 0;
    const size_t pixel_count = static_cast<size_t>(video_width) * video_height;
    if (pixel_count > MAX_VIDEO_PIXELS ||
        pixel_count + 2U > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        return 0;
    }

    const jsize required = static_cast<jsize>(pixel_count + 2U);
    if (!destination || env->GetArrayLength(destination) < required) return required;
    const jint dimensions[2] = {
        static_cast<jint>(video_width), static_cast<jint>(video_height)
    };
    env->SetIntArrayRegion(destination, 0, 2, dimensions);
    env->SetIntArrayRegion(
        destination, 2, static_cast<jsize>(pixel_count), video_pixels.data()
    );
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 0;
    }
    return required;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_LibretroNative_nativeCopyAudioSamples(
    JNIEnv* env, jobject, jshortArray destination
) {
    if (!destination) return 0;
    const jsize capacity = env->GetArrayLength(destination);
    if (capacity < 2) return 0;
    jshort* output = env->GetShortArrayElements(destination, nullptr);
    if (!output) return 0;

    size_t frames_to_read = 0;
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        frames_to_read = std::min(
            static_cast<size_t>(capacity / 2), audio_queued_frames
        );
        for (size_t frame = 0; frame < frames_to_read; ++frame) {
            const size_t offset = audio_read_frame * 2U;
            output[frame * 2U] = audio_ring[offset];
            output[frame * 2U + 1U] = audio_ring[offset + 1U];
            audio_read_frame = (audio_read_frame + 1U) % AUDIO_RING_CAPACITY_FRAMES;
        }
        audio_queued_frames -= frames_to_read;
    }
    env->ReleaseShortArrayElements(destination, output, 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 0;
    }
    return static_cast<jint>(frames_to_read * 2U);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_emulator_LibretroNative_nativeSerialize(JNIEnv* env, jobject) {
    if (!game_loaded || !serialize_size || !serialize_state) return nullptr;
    const size_t state_size = serialize_size();
    constexpr size_t MAX_SERIALIZED_STATE_BYTES = 256U * 1024U * 1024U;
    if (state_size == 0 || state_size > MAX_SERIALIZED_STATE_BYTES ||
        state_size > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        LOGE("Libretro core returned an invalid serialization size.");
        return nullptr;
    }
    std::vector<uint8_t> state;
    try {
        state.resize(state_size);
    } catch (...) {
        LOGE("Could not allocate Libretro save-state buffer.");
        return nullptr;
    }
    if (!serialize_state(state.data(), state.size())) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(state.size()));
    if (!result) return nullptr;
    env->SetByteArrayRegion(
        result, 0, static_cast<jsize>(state.size()),
        reinterpret_cast<const jbyte*>(state.data())
    );
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeUnserialize(
    JNIEnv* env, jobject, jbyteArray state_array
) {
    if (!game_loaded || !unserialize_state || !state_array) return JNI_FALSE;
    const jsize state_size = env->GetArrayLength(state_array);
    constexpr jsize MAX_SERIALIZED_STATE_BYTES = 256 * 1024 * 1024;
    if (state_size <= 0 || state_size > MAX_SERIALIZED_STATE_BYTES) return JNI_FALSE;
    std::vector<uint8_t> state;
    try {
        state.resize(static_cast<size_t>(state_size));
    } catch (...) {
        return JNI_FALSE;
    }
    env->GetByteArrayRegion(
        state_array, 0, state_size, reinterpret_cast<jbyte*>(state.data())
    );
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return JNI_FALSE;
    }
    return unserialize_state(state.data(), state.size()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeClose(JNIEnv*, jobject) {
    close_core();
}
