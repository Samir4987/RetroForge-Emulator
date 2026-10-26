cmake_minimum_required(VERSION 3.22.1)
project(retroforge_libretro LANGUAGES C CXX)

add_library(retroforge_libretro SHARED
    src/main/cpp/libretro_jni.cpp
)

target_compile_features(retroforge_libretro PRIVATE cxx_std_17)
target_compile_options(retroforge_libretro PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(retroforge_libretro PRIVATE android log dl)
