#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <string>

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
    // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10; accept 0RGB1555 and XRGB8888.
    if (command == 10 && data) {
        const auto format = *static_cast<PixelFormat*>(data);
        return format.format == 0 || format.format == 1 || format.format == 2;
    }
    // Unknown optional environment requests are intentionally unsupported.
    return false;
}
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

void close_core() {
    if (initialized && core_deinit) core_deinit();
    initialized = false;
    if (core) dlclose(core);
    core = nullptr;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_LibretroNative_nativeOpen(JNIEnv* env, jobject, jstring core_path, jstring rom_path) {
    close_core();
    const char* native_core_path = env->GetStringUTFChars(core_path, nullptr);
    const char* native_rom_path = env->GetStringUTFChars(rom_path, nullptr);
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
    const bool loaded = info.need_fullpath && load_game(&game);
    env->ReleaseStringUTFChars(rom_path, native_rom_path);
    if (!loaded) {
        close_core();
        return env->NewStringUTF("Core initialized, but ROM loading failed (core may require in-memory ROM data).");
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeRunFrame(JNIEnv*, jobject) {
    if (!initialized || !run_frame) return JNI_FALSE;
    run_frame();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeClose(JNIEnv*, jobject) {
    if (initialized && unload_game) unload_game();
    close_core();
}
