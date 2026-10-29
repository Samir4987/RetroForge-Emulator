#!/usr/bin/env bash
set -euo pipefail

# Build the pinned upstream melonDS DS Libretro core for supported Android ABIs.
# The core is GPL-3.0-or-later; keep its upstream license with the packaged binary.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/ndk/27.2.12479018}"
if [[ ! -d "$NDK_HOME" ]]; then
  echo "Android NDK not found: $NDK_HOME" >&2
  exit 1
fi

SOURCE_DIR="${RUNNER_TEMP:-$ROOT_DIR/build}/melonds-ds-src"
BUILD_ROOT="${RUNNER_TEMP:-$ROOT_DIR/build}/melonds-ds-build"
CORE_TAG="v1.1.8"
CORE_URL="https://git.libretro.com/libretro/melonds-ds.git"

rm -rf "$SOURCE_DIR"
git clone --depth 1 --branch "$CORE_TAG" "$CORE_URL" "$SOURCE_DIR"

for ABI in arm64-v8a x86_64; do
  BUILD_DIR="$BUILD_ROOT/$ABI"
  cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=24 \
    -DANDROID_STL=c++_shared \
    -DENABLE_OPENGL=OFF
  cmake --build "$BUILD_DIR" --parallel 2

  CORE_PATH="$(find "$BUILD_DIR" -type f \\( -name 'melondsds_libretro.so' -o -name 'melonds-ds_libretro.so' -o -name '*melonds*libretro*.so' \\) -print -quit)"
  if [[ -z "$CORE_PATH" ]]; then
    echo "No melonDS DS Libretro shared library produced for $ABI." >&2
    find "$BUILD_DIR" -type f -name '*.so' -print >&2 || true
    exit 1
  fi

  DEST="$ROOT_DIR/app/src/main/jniLibs/$ABI"
  mkdir -p "$DEST"
  cp "$CORE_PATH" "$DEST/melondsds_libretro_android.so"
done

LICENSE_PATH="$(find "$SOURCE_DIR" -maxdepth 2 -type f \\( -iname 'license' -o -iname 'license.txt' -o -iname 'copying' \\) -print -quit)"
if [[ -n "$LICENSE_PATH" ]]; then
  mkdir -p "$ROOT_DIR/app/src/main/assets/licenses"
  cp "$LICENSE_PATH" "$ROOT_DIR/app/src/main/assets/licenses/MELONDS-DS-LICENSE"
fi

echo "melonDS DS core packaged for arm64-v8a and x86_64."
