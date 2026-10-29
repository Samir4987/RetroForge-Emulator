#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <cctype>
#include <unordered_map>
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
JavaVM* java_vm = nullptr;
jweak java_bridge = nullptr;
jmethodID video_callback = nullptr;
jmethodID audio_callback = nullptr;
unsigned current_pixel_format = 1;
unsigned joypad_mask = 0;
float pointer_x = 0.5f, pointer_y = 0.5f;
bool pointer_pressed = false;
std::vector<int16_t> pending_audio;
std::string system_directory;
std::string save_directory;
std::string assets_directory;
std::unordered_map<std::string, std::string> core_options;
struct CoreVariable { const char* key; const char* value; };
using SerializeSize = size_t (*)();
using Serialize = bool (*)(void*, size_t);
using Unserialize = bool (*)(const void*, size_t);
SerializeSize serialize_size = nullptr;
Serialize serialize_state = nullptr;
Unserialize unserialize_state = nullptr;

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

JNIEnv* callback_env(bool& attached) {
    attached = false;
    if (!java_vm) return nullptr;
    JNIEnv* env = nullptr;
    jint status = java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) return env;
    if (status != JNI_EDETACHED || java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    attached = true;
    return env;
}
void detach_callback(bool attached) { if (attached && java_vm) java_vm->DetachCurrentThread(); }

bool environment(unsigned command, void* data) {
    // Strip Libretro experimental flag so flagged commands match their base IDs.
    command &= ~0x10000u;
    // RETRO_ENVIRONMENT_GET_CAN_DUPE
    if (command == 3 && data) { *static_cast<bool*>(data) = true; return true; }
    // RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY
    if (command == 9 && data && !system_directory.empty()) {
        *static_cast<const char**>(data) = system_directory.c_str(); return true;
    }
    // RETRO_ENVIRONMENT_GET_VARIABLE
    if (command == 15 && data) {
        auto* variable = static_cast<CoreVariable*>(data);
        auto it = variable->key ? core_options.find(variable->key) : core_options.end();
        if (it == core_options.end()) { variable->value = nullptr; return false; }
        variable->value = it->second.c_str(); return true;
    }
    // RETRO_ENVIRONMENT_SET_VARIABLES
    if (command == 16 && data) {
        auto* variables = static_cast<CoreVariable*>(data);
        for (auto* item = variables; item && item->key; ++item) {
            const std::string raw = item->value ? item->value : "";
            const auto separator = raw.find(';');
            if (separator == std::string::npos) continue;
            std::string choices = raw.substr(separator + 1);
            const auto first = choices.find('|');
            std::string value = choices.substr(0, first);
            const auto begin = value.find_first_not_of(" \\t");
            const auto end = value.find_last_not_of(" \\t");
            value = begin == std::string::npos ? "" : value.substr(begin, end - begin + 1);
            core_options[item->key] = value;
        }
        return true;
    }
    // RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE
    if (command == 17 && data) { *static_cast<bool*>(data) = false; return true; }
    // RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME
    if (command == 18) return true;
    // RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY
    if (command == 30 && data && !assets_directory.empty()) {
        *static_cast<const char**>(data) = assets_directory.c_str(); return true;
    }
    // RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY
    if (command == 31 && data && !save_directory.empty()) {
        *static_cast<const char**>(data) = save_directory.c_str(); return true;
    }
    // RETRO_ENVIRONMENT_GET_INPUT_BITMASKS
    if (command == 51 && data) { *static_cast<bool*>(data) = true; return true; }
    // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT
    if (command == 10 && data) {
        unsigned format = static_cast<PixelFormat*>(data)->format;
        if (format > 2) return false;
        current_pixel_format = format;
        return true;
    }
    return false;
}
void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !java_vm || !java_bridge || !video_callback || !width || !height ||
        width > 4096 || height > 4096) return;
    const size_t bpp = current_pixel_format == 1 ? 4 : 2;
    if (pitch < static_cast<size_t>(width) * bpp) return;
    size_t count = static_cast<size_t>(width) * height;
    if (count > static_cast<size_t>(std::numeric_limits<jsize>::max()) / 4) return;
    std::vector<uint32_t> pixels(count);
    const auto* src = static_cast<const uint8_t*>(data);
    for (unsigned y = 0; y < height; ++y) {
        const auto* row = src + static_cast<size_t>(y) * pitch;
        for (unsigned x = 0; x < width; ++x) {
            uint8_t r = 0, g = 0, b = 0;
            if (current_pixel_format == 1) {
                uint32_t px; std::memcpy(&px, row + static_cast<size_t>(x) * 4, 4);
                r=(px>>16)&255; g=(px>>8)&255; b=px&255;
            } else {
                uint16_t px; std::memcpy(&px, row + static_cast<size_t>(x) * 2, 2);
                if (current_pixel_format == 0) {
                    r=((px>>10)&31)*255/31; g=((px>>5)&31)*255/31; b=(px&31)*255/31;
                } else {
                    r=((px>>11)&31)*255/31; g=((px>>5)&63)*255/63; b=(px&31)*255/31;
                }
            }
            pixels[static_cast<size_t>(y)*width+x]=0xff000000u|(uint32_t(r)<<16)|(uint32_t(g)<<8)|b;
        }
    }
    bool attached=false; JNIEnv* env=callback_env(attached); if(!env) return;
    jobject bridge=env->NewLocalRef(java_bridge);
    if(bridge) {
        jbyteArray array=env->NewByteArray(static_cast<jsize>(count*4));
        if(array) {
            env->SetByteArrayRegion(array,0,static_cast<jsize>(count*4),reinterpret_cast<const jbyte*>(pixels.data()));
            env->CallVoidMethod(bridge,video_callback,array,static_cast<jint>(width),static_cast<jint>(height));
            env->DeleteLocalRef(array);
        }
        if(env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(bridge);
    }
    detach_callback(attached);
}
void send_audio(const int16_t* samples,size_t count) {
    if(!samples||!count||!java_vm||!java_bridge||!audio_callback||count>static_cast<size_t>(std::numeric_limits<jsize>::max())) return;
    bool attached=false; JNIEnv* env=callback_env(attached); if(!env) return;
    jobject bridge=env->NewLocalRef(java_bridge);
    if(bridge) {
        jshortArray array=env->NewShortArray(static_cast<jsize>(count));
        if(array) {
            env->SetShortArrayRegion(array,0,static_cast<jsize>(count),reinterpret_cast<const jshort*>(samples));
            env->CallVoidMethod(bridge,audio_callback,array);
            env->DeleteLocalRef(array);
        }
        if(env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(bridge);
    }
    detach_callback(attached);
}
void audio_sample(int16_t l,int16_t r) { pending_audio.push_back(l); pending_audio.push_back(r); }
size_t audio_sample_batch(const int16_t* samples,size_t frames) { if(samples&&frames) send_audio(samples,frames*2); return frames; }
void input_poll() {}
int16_t input_state(unsigned, unsigned device, unsigned, unsigned id) {
    if(device==1&&id<16) return (joypad_mask&(1u<<id))?1:0;
    if(device==6) {
        if(id==0) return static_cast<int16_t>((pointer_x*2.f-1.f)*32767.f);
        if(id==1) return static_cast<int16_t>((pointer_y*2.f-1.f)*32767.f);
        if(id==2) return pointer_pressed?1:0;
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
    serialize_size = nullptr; serialize_state = nullptr; unserialize_state = nullptr;
}

void close_core(JNIEnv* env = nullptr) {
    if (game_loaded && unload_game) unload_game();
    game_loaded = false;
    if (initialized && core_deinit) core_deinit();
    initialized = false;
    if (core) dlclose(core);
    core = nullptr;
    rom_data.clear(); pending_audio.clear(); core_options.clear();
    current_pixel_format = 1; joypad_mask = 0; pointer_x = 0.5f; pointer_y = 0.5f; pointer_pressed = false;
    if (env && java_bridge) env->DeleteWeakGlobalRef(java_bridge);
    java_bridge=nullptr; video_callback=nullptr; audio_callback=nullptr;
    reset_symbols();
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    java_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetDirectories(
        JNIEnv* env, jobject, jstring system_path, jstring save_path, jstring assets_path) {
    auto read_path = [env](jstring value) -> std::string {
        if (!value) return {};
        const char* chars = env->GetStringUTFChars(value, nullptr);
        if (!chars) return {};
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    };
    system_directory = read_path(system_path);
    save_directory = read_path(save_path);
    assets_directory = read_path(assets_path);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_LibretroNative_nativeOpen(JNIEnv* env, jobject bridge, jstring core_path, jstring rom_path) {
    close_core(env);
    java_bridge=env->NewWeakGlobalRef(bridge);
    jclass bridge_class=env->GetObjectClass(bridge);
    if(bridge_class) {
        video_callback=env->GetMethodID(bridge_class,"onNativeVideoFrame","([BII)V");
        audio_callback=env->GetMethodID(bridge_class,"onNativeAudioSamples","([S)V");
        env->DeleteLocalRef(bridge_class);
    }
    if(!java_bridge||!video_callback||!audio_callback) {
        close_core(env); return env->NewStringUTF("Could not bind Libretro video/audio callbacks.");
    }
    if (!core_path || !rom_path) {
        close_core(env);
        return env->NewStringUTF("Core path and ROM path are required.");
    }

    const char* native_core_path = env->GetStringUTFChars(core_path, nullptr);
    if (!native_core_path) {
        close_core(env);
        return env->NewStringUTF("Could not read core path.");
    }
    const char* native_rom_path = env->GetStringUTFChars(rom_path, nullptr);
    if (!native_rom_path) {
        env->ReleaseStringUTFChars(core_path, native_core_path);
        close_core(env);
        return env->NewStringUTF("Could not read ROM path.");
    }

    core = dlopen(native_core_path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(core_path, native_core_path);
    if (!core) {
        const char* error = dlerror();
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        close_core(env);
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
        close_core(env);
        env->ReleaseStringUTFChars(rom_path, native_rom_path);
        return env->NewStringUTF("Core is missing required Libretro API symbols.");
    }

    serialize_size=reinterpret_cast<SerializeSize>(dlsym(core,"retro_serialize_size"));
    serialize_state=reinterpret_cast<Serialize>(dlsym(core,"retro_serialize"));
    unserialize_state=reinterpret_cast<Unserialize>(dlsym(core,"retro_unserialize"));
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
            close_core(env);
            return env->NewStringUTF("Could not open ROM file.");
        }
        const std::streamoff length = input.tellg();
        if (length <= 0 || static_cast<unsigned long long>(length) >
                static_cast<unsigned long long>(std::numeric_limits<size_t>::max())) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core(env);
            return env->NewStringUTF("ROM file is empty or too large.");
        }
        rom_data.resize(static_cast<size_t>(length));
        input.seekg(0, std::ios::beg);
        if (!input.read(reinterpret_cast<char*>(rom_data.data()), length)) {
            env->ReleaseStringUTFChars(rom_path, native_rom_path);
            close_core(env);
            return env->NewStringUTF("Could not read complete ROM file.");
        }
        game.data = rom_data.data();
        game.size = rom_data.size();
    }

    const bool loaded = load_game(&game);
    env->ReleaseStringUTFChars(rom_path, native_rom_path);
    if (!loaded) {
        close_core(env);
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
    if(!pending_audio.empty()) { send_audio(pending_audio.data(),pending_audio.size()); pending_audio.clear(); }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeSetInputState(JNIEnv*, jobject, jint mask, jfloat x, jfloat y, jboolean touching) {
    joypad_mask=static_cast<unsigned>(mask);
    pointer_x=std::max(0.f,std::min(1.f,x)); pointer_y=std::max(0.f,std::min(1.f,y));
    pointer_pressed=touching==JNI_TRUE;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_emulator_LibretroNative_nativeSerialize(JNIEnv* env, jobject) {
    if(!game_loaded||!serialize_size||!serialize_state) return nullptr;
    size_t size=serialize_size();
    if(!size||size>64*1024*1024||size>static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    std::vector<uint8_t> state(size); if(!serialize_state(state.data(),state.size())) return nullptr;
    jbyteArray result=env->NewByteArray(static_cast<jsize>(size));
    if(result) env->SetByteArrayRegion(result,0,static_cast<jsize>(size),reinterpret_cast<const jbyte*>(state.data()));
    return result;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeUnserialize(JNIEnv* env, jobject, jbyteArray state) {
    if(!game_loaded||!unserialize_state||!state) return JNI_FALSE;
    jsize size=env->GetArrayLength(state); if(size<=0||size>64*1024*1024) return JNI_FALSE;
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    env->GetByteArrayRegion(state,0,size,reinterpret_cast<jbyte*>(bytes.data()));
    if(env->ExceptionCheck()){env->ExceptionClear();return JNI_FALSE;}
    return unserialize_state(bytes.data(),bytes.size())?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_example_emulator_LibretroNative_nativeClose(JNIEnv* env, jobject) { close_core(env); }
