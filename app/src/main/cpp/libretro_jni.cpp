#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <atomic>
#include <array>
#include <algorithm>
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
std::mutex video_mutex;
std::vector<uint32_t> video_pixels;
unsigned video_width = 0;
unsigned video_height = 0;
unsigned pixel_format = 0;
std::atomic<uint16_t> joypad_mask{0};
std::atomic<int16_t> analog_left_x{0}, analog_left_y{0};
std::atomic<int16_t> analog_right_x{0}, analog_right_y{0};
constexpr size_t kAudioCapacityFrames = 16384;
std::array<int16_t, kAudioCapacityFrames * 2> audio_ring{};
size_t audio_read_frame = 0;
size_t audio_write_frame = 0;
size_t audio_queued_frames = 0;
std::mutex audio_mutex;
double audio_sample_rate = 0.0;

void enqueue_audio(const int16_t* samples, size_t frames) {
    if (!samples) return;
    std::lock_guard<std::mutex> lock(audio_mutex);
    for (size_t frame = 0; frame < frames; ++frame) {
        if (audio_queued_frames == kAudioCapacityFrames) {
            audio_read_frame = (audio_read_frame + 1) % kAudioCapacityFrames;
            --audio_queued_frames; // Drop oldest samples to keep latency bounded.
        }
        const size_t dst = audio_write_frame * 2;
        audio_ring[dst] = samples[frame * 2];
        audio_ring[dst + 1] = samples[frame * 2 + 1];
        audio_write_frame = (audio_write_frame + 1) % kAudioCapacityFrames;
        ++audio_queued_frames;
    }
}
constexpr unsigned kMaxVideoDimension = 1024;

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
        const auto requested = *static_cast<PixelFormat*>(data);
        if (requested.format != 0 && requested.format != 1 && requested.format != 2) {
            return false;
        }
        std::lock_guard<std::mutex> lock(video_mutex);
        pixel_format = requested.format;
        return true;
    }
    return false;
}

// Video frames are converted to Android-friendly opaque ARGB_8888 pixels.
// Libretro permits null data to mean "reuse the previous frame"; in that case
// the last valid frame is deliberately retained.
void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0 ||
        width > kMaxVideoDimension || height > kMaxVideoDimension ||
        static_cast<size_t>(width) > std::numeric_limits<size_t>::max() / 4) {
        return;
    }

    unsigned format;
    {
        std::lock_guard<std::mutex> lock(video_mutex);
        format = pixel_format;
    }
    const size_t bytes_per_pixel = format == 1 ? 4u : 2u;
    const size_t row_bytes = static_cast<size_t>(width) * bytes_per_pixel;
    if (pitch < row_bytes || static_cast<size_t>(height) >
            std::numeric_limits<size_t>::max() / static_cast<size_t>(width)) {
        return;
    }

    try {
        std::vector<uint32_t> converted(static_cast<size_t>(width) * height);
        const auto* source = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            const uint8_t* row = source + static_cast<size_t>(y) * pitch;
            for (unsigned x = 0; x < width; ++x) {
                uint32_t red, green, blue;
                if (format == 1) {
                    uint32_t value;
                    std::memcpy(&value, row + static_cast<size_t>(x) * 4u, sizeof(value));
                    red = (value >> 16u) & 0xffu;
                    green = (value >> 8u) & 0xffu;
                    blue = value & 0xffu;
                } else {
                    uint16_t value;
                    std::memcpy(&value, row + static_cast<size_t>(x) * 2u, sizeof(value));
                    if (format == 0) { // RETRO_PIXEL_FORMAT_0RGB1555
                        red = ((value >> 10u) & 0x1fu) * 255u / 31u;
                        green = ((value >> 5u) & 0x1fu) * 255u / 31u;
                        blue = (value & 0x1fu) * 255u / 31u;
                    } else { // RETRO_PIXEL_FORMAT_RGB565
                        red = ((value >> 11u) & 0x1fu) * 255u / 31u;
                        green = ((value >> 5u) & 0x3fu) * 255u / 63u;
                        blue = (value & 0x1fu) * 255u / 31u;
                    }
                }
                converted[static_cast<size_t>(y) * width + x] =
                    0xff000000u | (red << 16u) | (green << 8u) | blue;
            }
        }

        std::lock_guard<std::mutex> lock(video_mutex);
        video_pixels.swap(converted);
        video_width = width;
        video_height = height;
    } catch (...) {
        // Never allow allocation/conversion failures to unwind through a core callback.
    }
}
void audio_sample(int16_t left, int16_t right) {
    const int16_t samples[2] = {left, right};
    enqueue_audio(samples, 1);
}
size_t audio_sample_batch(const int16_t* samples, size_t frames) {
    enqueue_audio(samples, frames);
    return samples ? frames : 0;
}
void input_poll() {}
int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;

    // RETRO_DEVICE_JOYPAD = 1; standard Libretro joypad IDs occupy bits 0..15.
    if (device == 1 && index == 0 && id < 16) {
        return (joypad_mask.load(std::memory_order_relaxed) & (uint16_t{1} << id)) != 0 ? 1 : 0;
    }

    // RETRO_DEVICE_ANALOG = 5; index 0 is left stick, index 1 is right stick.
    // Axis IDs 0/1 are X/Y. The N64 C-buttons are supplied on the right stick.
    if (device == 5 && id < 2) {
        if (index == 0) {
            return id == 0 ? analog_left_x.load(std::memory_order_relaxed)
                           : analog_left_y.load(std::memory_order_relaxed);
        }
        if (index == 1) {
            return id == 0 ? analog_right_x.load(std::memory_order_relaxed)
                           : analog_right_y.load(std::memory_order_relaxed);
        }
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
    {
        std::lock_guard<std::mutex> lock(video_mutex);
        video_pixels.clear();
        video_width = 0;
        video_height = 0;
        pixel_format = 0;
        joypad_mask.store(0, std::memory_order_relaxed);
        analog_left_x.store(0, std::memory_order_relaxed);
        analog_left_y.store(0, std::memory_order_relaxed);
        analog_right_x.store(0, std::memory_order_relaxed);
        analog_right_y.store(0, std::memory_order_relaxed);
    }
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        audio_read_frame = 0;
        audio_write_frame = 0;
        audio_queued_frames = 0;
        audio_sample_rate = 0.0;
    }
    reset_symbols();
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
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        audio_sample_rate = av_info.sample_rate;
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
Java_com_example_emulator_LibretroNative_nativeClose(JNIEnv*, jobject) {
    close_core();
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_emulator_LibretroNative_nativeVideoFrameInfo(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(video_mutex);
    if (video_width == 0 || video_height == 0) return nullptr;
    jint dimensions[2] = {static_cast<jint>(video_width), static_cast<jint>(video_height)};
    jintArray result = env->NewIntArray(2);
    if (result) env->SetIntArrayRegion(result, 0, 2, dimensions);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeCopyVideoFrame(JNIEnv* env, jobject, jintArray destination) {
    if (!destination) return JNI_FALSE;
    std::vector<jint> snapshot;
    {
        std::lock_guard<std::mutex> lock(video_mutex);
        if (video_pixels.empty() || env->GetArrayLength(destination) <
                static_cast<jsize>(video_pixels.size())) {
            return JNI_FALSE;
        }
        try {
            snapshot.assign(video_pixels.begin(), video_pixels.end());
        } catch (...) {
            return JNI_FALSE;
        }
    }
    env->SetIntArrayRegion(destination, 0, static_cast<jsize>(snapshot.size()), snapshot.data());
    return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetJoypadState(JNIEnv*, jobject, jint mask) {
    joypad_mask.store(static_cast<uint16_t>(mask & 0xffff), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetAnalogState(
        JNIEnv*, jobject, jint left_x, jint left_y, jint right_x, jint right_y) {
    const auto clamp_axis = [](jint value) {
        return static_cast<int16_t>(std::clamp(value, -32768, 32767));
    };
    analog_left_x.store(clamp_axis(left_x), std::memory_order_relaxed);
    analog_left_y.store(clamp_axis(left_y), std::memory_order_relaxed);
    analog_right_x.store(clamp_axis(right_x), std::memory_order_relaxed);
    analog_right_y.store(clamp_axis(right_y), std::memory_order_relaxed);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_example_emulator_LibretroNative_nativeGetAudioSampleRate(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(audio_mutex);
    return static_cast<jdouble>(audio_sample_rate);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_emulator_LibretroNative_nativeReadAudio(JNIEnv* env, jobject, jshortArray destination) {
    if (!destination) return 0;
    const jsize capacity = env->GetArrayLength(destination);
    if (capacity < 2) return 0;
    std::vector<jshort> samples;
    {
        std::lock_guard<std::mutex> lock(audio_mutex);
        const size_t requested_frames = static_cast<size_t>(capacity / 2);
        const size_t frames = std::min(audio_queued_frames, requested_frames);
        try {
            samples.resize(frames * 2);
        } catch (...) {
            return 0;
        }
        for (size_t frame = 0; frame < frames; ++frame) {
            const size_t src = audio_read_frame * 2;
            samples[frame * 2] = audio_ring[src];
            samples[frame * 2 + 1] = audio_ring[src + 1];
            audio_read_frame = (audio_read_frame + 1) % kAudioCapacityFrames;
        }
        audio_queued_frames -= frames;
    }
    if (samples.empty()) return 0;
    env->SetShortArrayRegion(destination, 0, static_cast<jsize>(samples.size()), samples.data());
    if (env->ExceptionCheck()) return 0;
    return static_cast<jint>(samples.size());
}
