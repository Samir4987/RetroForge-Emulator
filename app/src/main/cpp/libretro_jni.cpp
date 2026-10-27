#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <fstream>
#include <limits>
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
        const auto format = *static_cast<PixelFormat*>(data);
        return format.format == 0 || format.format == 1 || format.format == 2;
    }
    return false;
}

// Playback pipelines are intentionally placeholders until video/audio/input are connected.
void video_refresh(const void*, unsigned, unsigned, size_t) {}
void audio_sample(int16_t, int16_t) {}
size_t audio_sample_batch(const int16_t*, size_t frames) { return frames; }
void input_poll() {}
int16_t input_state(unsigned, unsigned, unsigned, unsigned) { return 0; }

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
