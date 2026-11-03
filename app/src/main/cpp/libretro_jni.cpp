#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <dlfcn.h>
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <limits>
#include <mutex>
#include <new>
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

using HwContextCallback = void (*)();
using HwFramebufferCallback = uintptr_t (*)();
using HwProcAddressCallback = void* (*)(const char*);
struct HwRenderCallback {
    unsigned context_type;
    HwContextCallback context_reset;
    HwFramebufferCallback get_current_framebuffer;
    HwProcAddressCallback get_proc_address;
    bool depth;
    bool stencil;
    bool bottom_left_origin;
    unsigned version_major;
    unsigned version_minor;
    bool cache_context;
    HwContextCallback context_destroy;
    bool debug_context;
};
constexpr unsigned kSetHwRender = 14;
constexpr unsigned kGetPreferredHwRender = 56;
constexpr unsigned kHwContextOpenGles3 = 4;
constexpr unsigned kGlSurfaceSize = 2048;


constexpr size_t kMaxAudioSamples = 32768;
constexpr unsigned kJoypadDevice = 1;
constexpr unsigned kAnalogDevice = 5;
constexpr unsigned kPointerDevice = 6;
constexpr unsigned kMaxPorts = 2;
constexpr unsigned kJoypadButtons = 16;

void* core = nullptr;
bool initialized = false;
bool game_loaded = false;
AvInfo core_av_info{};
EGLDisplay egl_display = EGL_NO_DISPLAY;
EGLContext egl_context = EGL_NO_CONTEXT;
EGLSurface egl_surface = EGL_NO_SURFACE;
HwRenderCallback hw_render_callback{};
bool hw_context_ready = false;
std::vector<uint8_t> rom_data;
std::string system_directory;
std::string save_directory;
std::string content_directory;

std::recursive_mutex core_mutex;
std::mutex media_mutex;
std::vector<uint8_t> video_frame;
unsigned video_width = 0;
unsigned video_height = 0;
size_t video_pitch = 0;
unsigned video_pixel_format = 0; // Libretro 0RGB1555 until the core selects another format.
bool video_frame_ready = false;
std::vector<int16_t> audio_samples;
std::array<std::array<int16_t, kJoypadButtons>, kMaxPorts> joypad_state{};
std::array<std::array<int16_t, 4>, kMaxPorts> analog_state{};
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
using SerializeSize = size_t (*)();
using Serialize = bool (*)(void*, size_t);
using Unserialize = bool (*)(const void*, size_t);
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
SerializeSize serialize_size = nullptr;
Serialize serialize_state = nullptr;
Unserialize unserialize_state = nullptr;

bool make_gles_current() {
    return egl_display != EGL_NO_DISPLAY && egl_context != EGL_NO_CONTEXT &&
           egl_surface != EGL_NO_SURFACE &&
           eglMakeCurrent(egl_display, egl_surface, egl_surface, egl_context) == EGL_TRUE;
}

void release_gles_current() {
    if (egl_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
}

uintptr_t current_framebuffer() { return 0; }

void* get_gl_proc_address(const char* name) {
    return name ? reinterpret_cast<void*>(eglGetProcAddress(name)) : nullptr;
}

bool create_gles_context() {
    if (hw_context_ready) return make_gles_current();
    egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (egl_display == EGL_NO_DISPLAY || eglInitialize(egl_display, nullptr, nullptr) != EGL_TRUE) {
        LOGE("Could not initialize EGL display (0x%x)", eglGetError());
        return false;
    }
    if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
        LOGE("Could not bind OpenGL ES API (0x%x)", eglGetError());
        return false;
    }

    const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16, EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (eglChooseConfig(egl_display, config_attributes, &config, 1, &config_count) != EGL_TRUE ||
        config_count == 0) {
        LOGE("No EGL config supports GLES3 pbuffer (0x%x)", eglGetError());
        return false;
    }

    const EGLint surface_attributes[] = {
        EGL_WIDTH, static_cast<EGLint>(kGlSurfaceSize),
        EGL_HEIGHT, static_cast<EGLint>(kGlSurfaceSize), EGL_NONE
    };
    egl_surface = eglCreatePbufferSurface(egl_display, config, surface_attributes);
    if (egl_surface == EGL_NO_SURFACE) {
        LOGE("Could not create EGL pbuffer (0x%x)", eglGetError());
        return false;
    }

    const EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    egl_context = eglCreateContext(egl_display, config, EGL_NO_CONTEXT, context_attributes);
    if (egl_context == EGL_NO_CONTEXT) {
        LOGE("Could not create GLES3 context (0x%x)", eglGetError());
        return false;
    }
    if (!make_gles_current()) {
        LOGE("Could not make GLES3 context current (0x%x)", eglGetError());
        return false;
    }

    hw_context_ready = true;
    if (hw_render_callback.context_reset) hw_render_callback.context_reset();
    return true;
}

void destroy_gles_context() {
    if (egl_display == EGL_NO_DISPLAY) return;
    if (make_gles_current() && hw_render_callback.context_destroy) {
        hw_render_callback.context_destroy();
    }
    release_gles_current();
    if (egl_context != EGL_NO_CONTEXT) eglDestroyContext(egl_display, egl_context);
    if (egl_surface != EGL_NO_SURFACE) eglDestroySurface(egl_display, egl_surface);
    egl_context = EGL_NO_CONTEXT;
    egl_surface = EGL_NO_SURFACE;
    eglTerminate(egl_display);
    egl_display = EGL_NO_DISPLAY;
    hw_context_ready = false;
    hw_render_callback = HwRenderCallback{};
}

bool environment(unsigned command, void* data) {
    if (command == 9 && data) {
        *static_cast<const char**>(data) = system_directory.c_str();
        return !system_directory.empty();
    }
    if (command == 30 && data) {
        *static_cast<const char**>(data) = content_directory.c_str();
        return !content_directory.empty();
    }
    if (command == 31 && data) {
        *static_cast<const char**>(data) = save_directory.c_str();
        return !save_directory.empty();
    }
    if (command == 10 && data) {
        const auto format = *static_cast<PixelFormat*>(data);
        if (format.format > 2) return false;
        std::lock_guard<std::mutex> lock(media_mutex);
        video_pixel_format = format.format;
        return true;
    }
    if (command == kGetPreferredHwRender && data) {
        *static_cast<unsigned*>(data) = kHwContextOpenGles3;
        return true;
    }
    if (command == kSetHwRender && data) {
        auto* callback = static_cast<HwRenderCallback*>(data);
        if (callback->context_type != kHwContextOpenGles3) {
            LOGE("Unsupported Libretro hardware context type: %u", callback->context_type);
            return false;
        }
        hw_render_callback = *callback;
        callback->get_current_framebuffer = current_framebuffer;
        callback->get_proc_address = get_gl_proc_address;
        return create_gles_context();
    }
    return false;
}

void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (width == 0 || height == 0 || width > 4096 || height > 4096) return;
    const bool hardware_frame =
        data == reinterpret_cast<const void*>(static_cast<intptr_t>(-1));
    std::vector<uint8_t> captured;
    const void* source = data;
    size_t source_pitch = pitch;

    if (hardware_frame) {
        if (!make_gles_current() || width > kGlSurfaceSize || height > kGlSurfaceSize) return;
        glFinish();
        if (static_cast<size_t>(width) > std::numeric_limits<size_t>::max() / 4) return;
        source_pitch = static_cast<size_t>(width) * 4;
        if (static_cast<size_t>(height) > std::numeric_limits<size_t>::max() / source_pitch) return;
        const size_t bytes = source_pitch * static_cast<size_t>(height);
        std::vector<uint8_t> rgba(bytes);
        captured.resize(bytes);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, static_cast<GLsizei>(width), static_cast<GLsizei>(height),
                     GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
        // Convert GL's bottom-left RGBA rows to top-left XRGB8888 memory.
        for (unsigned y = 0; y < height; ++y) {
            const size_t source_row = static_cast<size_t>(height - 1 - y) * source_pitch;
            const size_t target_row = static_cast<size_t>(y) * source_pitch;
            for (unsigned x = 0; x < width; ++x) {
                const size_t src = source_row + static_cast<size_t>(x) * 4;
                const size_t dst = target_row + static_cast<size_t>(x) * 4;
                captured[dst] = rgba[src + 2];
                captured[dst + 1] = rgba[src + 1];
                captured[dst + 2] = rgba[src];
                captured[dst + 3] = 0;
            }
        }
        source = captured.data();
    } else {
        if (!data) return;
        unsigned pixel_format;
        {
            std::lock_guard<std::mutex> lock(media_mutex);
            pixel_format = video_pixel_format;
        }
        const size_t bytes_per_pixel = pixel_format == 1 ? 4U : 2U;
        if (static_cast<size_t>(width) >
            std::numeric_limits<size_t>::max() / bytes_per_pixel) return;
        const size_t minimum_pitch = static_cast<size_t>(width) * bytes_per_pixel;
        if (pitch < minimum_pitch ||
            static_cast<size_t>(height) > std::numeric_limits<size_t>::max() / pitch) return;
        source_pitch = pitch;
    }

    const size_t bytes = source_pitch * static_cast<size_t>(height);
    constexpr size_t kMaxVideoFrameBytes = 64U * 1024U * 1024U;
    if (bytes == 0 || bytes > kMaxVideoFrameBytes) return;

    std::lock_guard<std::mutex> lock(media_mutex);
    video_frame.resize(bytes);
    std::memcpy(video_frame.data(), source, bytes);
    video_width = width;
    video_height = height;
    video_pitch = source_pitch;
    if (hardware_frame) video_pixel_format = 1;
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
    if (!data || frames == 0 ||
        frames > std::numeric_limits<size_t>::max() / 2) return 0;
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
        return analog_state[port][index * 2 + id];
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
    serialize_size = nullptr; serialize_state = nullptr; unserialize_state = nullptr;
}

void reset_frontend_state() {
    std::lock_guard<std::mutex> lock(media_mutex);
    video_frame.clear();
    video_width = video_height = 0;
    video_pitch = 0;
    video_pixel_format = 0;
    video_frame_ready = false;
    core_av_info = AvInfo{};
    audio_samples.clear();
    for (auto& port : joypad_state) port.fill(0);
    for (auto& port : analog_state) port.fill(0);
    pointer_x = pointer_y = 0;
    pointer_pressed = false;
}

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (value_) chars_ = env_->GetStringUTFChars(value_, nullptr);
    }
    ~ScopedUtfChars() {
        if (chars_) env_->ReleaseStringUTFChars(value_, chars_);
    }
    const char* get() const { return chars_; }
    ScopedUtfChars(const ScopedUtfChars&) = delete;
    ScopedUtfChars& operator=(const ScopedUtfChars&) = delete;
private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_ = nullptr;
};

void close_core() {
    if (hw_context_ready) make_gles_current();
    if (game_loaded && unload_game) unload_game();
    game_loaded = false;
    if (initialized && core_deinit) core_deinit();
    initialized = false;
    destroy_gles_context();
    if (core) dlclose(core);
    core = nullptr;
    rom_data.clear();
    reset_symbols();
    reset_frontend_state();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_emulator_LibretroNative_nativeOpen(
    JNIEnv* env, jobject, jstring core_path, jstring rom_path,
    jstring system_path, jstring save_path, jstring content_path) {
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    close_core();
    if (!core_path || !rom_path || !system_path || !save_path || !content_path) {
        return env->NewStringUTF("Core, ROM, system, save, and content paths are required.");
    }

    ScopedUtfChars native_core_path(env, core_path);
    ScopedUtfChars native_rom_path(env, rom_path);
    ScopedUtfChars native_system_path(env, system_path);
    ScopedUtfChars native_save_path(env, save_path);
    ScopedUtfChars native_content_path(env, content_path);
    if (!native_core_path.get() || !native_rom_path.get() ||
        !native_system_path.get() || !native_save_path.get() || !native_content_path.get()) {
        return env->NewStringUTF("Could not read one or more native paths.");
    }

    system_directory = native_system_path.get();
    save_directory = native_save_path.get();
    content_directory = native_content_path.get();

    core = dlopen(native_core_path.get(), RTLD_NOW | RTLD_LOCAL);
    if (!core) {
        const char* error = dlerror();
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
        return env->NewStringUTF("Core is missing required Libretro API symbols.");
    }
    // Save-state support is optional in Libretro. Expose it only when all
    // three serialization entry points are exported by the selected core.
    serialize_size = reinterpret_cast<SerializeSize>(dlsym(core, "retro_serialize_size"));
    serialize_state = reinterpret_cast<Serialize>(dlsym(core, "retro_serialize"));
    unserialize_state = reinterpret_cast<Unserialize>(dlsym(core, "retro_unserialize"));
    if (!serialize_size || !serialize_state || !unserialize_state) {
        serialize_size = nullptr;
        serialize_state = nullptr;
        unserialize_state = nullptr;
    }

    if (api_version() != 1U) {
        close_core();
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
        close_core();
        return env->NewStringUTF("Core returned incomplete system information.");
    }

    GameInfo game{native_rom_path.get(), nullptr, 0, nullptr};
    if (!info.need_fullpath) {
        std::ifstream input(native_rom_path.get(), std::ios::binary | std::ios::ate);
        if (!input) {
            close_core();
            return env->NewStringUTF("Could not open ROM file.");
        }
        const std::streamoff length = input.tellg();
        constexpr std::streamoff kMaxInMemoryRomBytes = 512LL * 1024LL * 1024LL;
        if (length <= 0 || length > kMaxInMemoryRomBytes ||
            static_cast<unsigned long long>(length) >
                static_cast<unsigned long long>(std::numeric_limits<size_t>::max())) {
            close_core();
            return env->NewStringUTF(
                "ROM is empty or too large for this core's in-memory loading mode (512 MiB limit)."
            );
        }
        try {
            rom_data.resize(static_cast<size_t>(length));
        } catch (const std::bad_alloc&) {
            close_core();
            return env->NewStringUTF("Not enough memory to stage this ROM for the Libretro core.");
        }
        input.seekg(0, std::ios::beg);
        if (!input.read(reinterpret_cast<char*>(rom_data.data()), static_cast<std::streamsize>(length))) {
            close_core();
            return env->NewStringUTF("Could not read complete ROM file.");
        }
        game.data = rom_data.data();
        game.size = rom_data.size();
    }

    if (!load_game(&game)) {
        close_core();
        return env->NewStringUTF("Libretro core rejected the ROM.");
    }
    game_loaded = true;
    get_av_info(&core_av_info);
    // EGL contexts are thread-affine. nativeOpen runs during ROM loading;
    // release here so the frame worker can make the context current itself.
    if (hw_context_ready) release_gles_current();
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeRunFrame(JNIEnv*, jobject) {
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    if (!initialized || !game_loaded || !run_frame) return JNI_FALSE;
    if (hw_context_ready && !make_gles_current()) return JNI_FALSE;
    run_frame();
    if (hw_context_ready) release_gles_current();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_example_emulator_LibretroNative_nativeGetAvInfo(JNIEnv* env, jobject) {
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    jdouble values[7];
    values[0] = core_av_info.base_width;
    values[1] = core_av_info.base_height;
    values[2] = core_av_info.max_width;
    values[3] = core_av_info.max_height;
    values[4] = core_av_info.aspect_ratio;
    values[5] = core_av_info.fps;
    values[6] = core_av_info.sample_rate;
    jdoubleArray result = env->NewDoubleArray(7);
    if (result) env->SetDoubleArrayRegion(result, 0, 7, values);
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_emulator_LibretroNative_nativeSerializeState(JNIEnv* env, jobject) {
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    if (!game_loaded || !serialize_size || !serialize_state) return nullptr;
    const size_t size = serialize_size();
    constexpr size_t kMaxSerializedStateBytes = 256U * 1024U * 1024U;
    if (size == 0 || size > kMaxSerializedStateBytes ||
        size > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    std::vector<uint8_t> state(size);
    if (!serialize_state(state.data(), size)) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(size),
                                reinterpret_cast<const jbyte*>(state.data()));
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_emulator_LibretroNative_nativeUnserializeState(
    JNIEnv* env, jobject, jbyteArray serialized) {
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    if (!game_loaded || !unserialize_state || !serialized) return JNI_FALSE;
    const jsize size = env->GetArrayLength(serialized);
    constexpr jsize kMaxSerializedStateBytes = 256 * 1024 * 1024;
    if (size <= 0 || size > kMaxSerializedStateBytes) return JNI_FALSE;
    jbyte* bytes = env->GetByteArrayElements(serialized, nullptr);
    if (!bytes) return JNI_FALSE;
    const bool restored = unserialize_state(bytes, static_cast<size_t>(size));
    env->ReleaseByteArrayElements(serialized, bytes, JNI_ABORT);
    return restored ? JNI_TRUE : JNI_FALSE;
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
    size_t count = std::min(audio_samples.size(), static_cast<size_t>(capacity));
    count -= count % 2; // Keep interleaved stereo L/R samples frame-aligned.
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
    analog_state[static_cast<size_t>(port)][static_cast<size_t>(stick * 2 + axis)] =
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
    std::lock_guard<std::recursive_mutex> core_lock(core_mutex);
    close_core();
}
