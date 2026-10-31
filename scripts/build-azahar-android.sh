#!/usr/bin/env bash
# Build the pinned Azahar Libretro core for Android ARM64-v8a.
# This is deliberately separate from the regular RetroForge APK build.
set -euo pipefail

readonly AZAHAR_REPOSITORY="https://github.com/azahar-emu/azahar.git"
readonly AZAHAR_REVISION="01623d2fb707317410225c0eced82e3e0c84edc1"
readonly ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
readonly ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-21}"
readonly ANDROID_NDK_VERSION="29.0.14206865"
readonly SOURCE_DIR="${AZAHAR_SOURCE_DIR:-${PWD}/.build/azahar-src}"
readonly BUILD_DIR="${AZAHAR_BUILD_DIR:-${PWD}/.build/azahar-android-${ANDROID_ABI}}"
readonly OUTPUT_DIR="${AZAHAR_OUTPUT_DIR:-${PWD}/.build/azahar-artifacts/${ANDROID_ABI}}"

if [[ "${ANDROID_ABI}" != "arm64-v8a" ]]; then
  echo "Only arm64-v8a is validated by the pinned upstream Android Libretro CI job." >&2
  exit 2
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    ANDROID_NDK_HOME="${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}"
  elif [[ -n "${ANDROID_HOME:-}" ]]; then
    ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/${ANDROID_NDK_VERSION}"
  else
    echo "Set ANDROID_NDK_HOME or ANDROID_SDK_ROOT/ANDROID_HOME." >&2
    exit 2
  fi
fi
export ANDROID_NDK_HOME

if [[ ! -f "${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK ${ANDROID_NDK_VERSION} not found at ${ANDROID_NDK_HOME}" >&2
  exit 2
fi
command -v cmake >/dev/null || { echo "CMake >= 3.25 is required." >&2; exit 2; }
command -v git >/dev/null
cmake --version | head -n 1

cmake_version="$(cmake --version | awk 'NR == 1 {print $3}')"
if [[ "$(printf '%s\n' 3.25 "${cmake_version}" | sort -V | head -n 1)" != "3.25" ]]; then
  echo "CMake ${cmake_version} is too old; Azahar requires >= 3.25." >&2
  exit 2
fi

mkdir -p "$(dirname "${SOURCE_DIR}")" "${OUTPUT_DIR}"
if [[ ! -d "${SOURCE_DIR}/.git" ]]; then
  git clone --filter=blob:none --recurse-submodules "${AZAHAR_REPOSITORY}" "${SOURCE_DIR}"
fi
git -C "${SOURCE_DIR}" fetch --no-tags origin "${AZAHAR_REVISION}"
git -C "${SOURCE_DIR}" checkout --detach "${AZAHAR_REVISION}"
git -C "${SOURCE_DIR}" submodule sync --recursive
git -C "${SOURCE_DIR}" submodule update --init --recursive

actual_revision="$(git -C "${SOURCE_DIR}" rev-parse HEAD)"
if [[ "${actual_revision}" != "${AZAHAR_REVISION}" ]]; then
  echo "Source revision mismatch: expected ${AZAHAR_REVISION}, got ${actual_revision}" >&2
  exit 1
fi

cmake -S "${SOURCE_DIR}" -B "${BUILD_DIR}" \
  -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="${ANDROID_ABI}" \
  -DANDROID_PLATFORM="android-${ANDROID_API_LEVEL}" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DENABLE_LIBRETRO=ON \
  -DENABLE_TESTS=OFF

cmake --build "${BUILD_DIR}" --target azahar_libretro --parallel "${BUILD_JOBS:-2}"

core_path="${BUILD_DIR}/bin/Release/azahar_libretro.so"
if [[ ! -s "${core_path}" ]]; then
  core_path="$(find "${BUILD_DIR}" -type f -name azahar_libretro.so -print -quit)"
fi
if [[ -z "${core_path}" || ! -s "${core_path}" ]]; then
  echo "Build completed but azahar_libretro.so was not found." >&2
  exit 1
fi

readelf_tool="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if [[ ! -x "${readelf_tool}" ]]; then
  readelf_tool="$(command -v llvm-readelf || command -v readelf || true)"
fi
if [[ -z "${readelf_tool}" ]]; then
  echo "Could not find llvm-readelf/readelf to verify the core." >&2
  exit 1
fi

"${readelf_tool}" -h "${core_path}" | grep -E 'Class:|Machine:'
symbols="$("${readelf_tool}" --dyn-syms --wide "${core_path}")"
for symbol in retro_api_version retro_init retro_deinit retro_get_system_info retro_get_system_av_info retro_load_game retro_run retro_unload_game; do
  if ! grep -Eq "[[:space:]]${symbol}$" <<<"${symbols}"; then
    echo "Required Libretro export missing: ${symbol}" >&2
    exit 1
  fi
done

install -m 0644 "${core_path}" "${OUTPUT_DIR}/azahar_libretro.so"
cp "${SOURCE_DIR}/LICENSE.txt" "${OUTPUT_DIR}/AZAHAR-LICENSE.txt"
printf '%s\n' "${AZAHAR_REVISION}" > "${OUTPUT_DIR}/AZAHAR-REVISION.txt"
echo "Verified core artifact: ${OUTPUT_DIR}/azahar_libretro.so"
