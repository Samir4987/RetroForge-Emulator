#!/usr/bin/env bash
set -euo pipefail

# Build the pinned upstream Libretro mGBA core for Android.
# GB, GBC, and GBA are enabled; no ROMs or proprietary BIOS files are bundled.
readonly MGBA_REPOSITORY="https://github.com/libretro/mgba.git"
readonly MGBA_REVISION="7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6"
readonly ANDROID_MIN_API="24"
readonly ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" ]]; then
  echo "Set ANDROID_SDK_ROOT (or ANDROID_HOME) before building mGBA." >&2
  exit 1
fi

ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-}"
if [[ -z "$ANDROID_NDK_ROOT" ]]; then
  if [[ -d "$ANDROID_SDK_ROOT/ndk/27.2.12479018" ]]; then
    ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
  else
    shopt -s nullglob
    installed_ndks=("$ANDROID_SDK_ROOT"/ndk/*)
    shopt -u nullglob
    if (( ${#installed_ndks[@]} == 0 )); then
      echo "No Android NDK found under $ANDROID_SDK_ROOT/ndk." >&2
      exit 1
    fi
    ANDROID_NDK_ROOT="${installed_ndks[0]}"
  fi
fi
if [[ ! -f "$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK toolchain not found under $ANDROID_NDK_ROOT." >&2
  exit 1
fi

WORK_ROOT="${MGBA_BUILD_ROOT:-${RUNNER_TEMP:-/tmp}/retroforge-mgba-build}"
SOURCE_DIR="$WORK_ROOT/source"
BUILD_ROOT="$WORK_ROOT/build"
APP_JNI_LIBS="${GITHUB_WORKSPACE:-$(pwd)}/app/src/main/jniLibs"
LICENSE_DEST="${GITHUB_WORKSPACE:-$(pwd)}/app/src/main/assets/licenses/mgba-MPL-2.0.txt"
mkdir -p "$WORK_ROOT" "$BUILD_ROOT"

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
  git clone --filter=blob:none "$MGBA_REPOSITORY" "$SOURCE_DIR"
fi
git -C "$SOURCE_DIR" fetch --depth=1 origin "$MGBA_REVISION"
git -C "$SOURCE_DIR" checkout --detach "$MGBA_REVISION"
actual_revision="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
if [[ "$actual_revision" != "$MGBA_REVISION" ]]; then
  echo "mGBA revision mismatch: expected $MGBA_REVISION, got $actual_revision" >&2
  exit 1
fi

for abi in "${ABIS[@]}"; do
  build_dir="$BUILD_ROOT/$abi"
  echo "Building mGBA Libretro core for $abi at $MGBA_REVISION"
  cmake -S "$SOURCE_DIR" -B "$build_dir" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$ANDROID_MIN_API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_LIBRETRO=ON \
    -DBUILD_QT=OFF \
    -DBUILD_SDL=OFF \
    -DBUILD_SHARED=OFF \
    -DSKIP_LIBRARY=ON \
    -DDISABLE_DEPS=ON \
    -DENABLE_SCRIPTING=OFF \
    -DUSE_FFMPEG=OFF \
    -DUSE_MINIZIP=OFF \
    -DUSE_LIBZIP=OFF \
    -DUSE_EPOXY=OFF \
    -DUSE_EDITLINE=OFF \
    -DUSE_LZMA=OFF \
    -DUSE_DISCORD_RPC=OFF \
    -DUSE_SQLITE3=OFF \
    -DUSE_ELF=OFF \
    -DUSE_FREETYPE=OFF \
    -DUSE_JSON_C=OFF \
    -DUSE_PNG=OFF \
    -DUSE_ZLIB=OFF \
    -DM_CORE_GBA=ON \
    -DM_CORE_GB=ON

  cmake --build "$build_dir" --target mgba_libretro --parallel 2
  core_path="$build_dir/mgba_libretro.so"
  if [[ ! -s "$core_path" ]]; then
    echo "Expected mGBA core output was not produced: $core_path" >&2
    find "$build_dir" -type f -name '*libretro*.so' -print >&2
    exit 1
  fi

  mkdir -p "$APP_JNI_LIBS/$abi"
  install -m 0644 "$core_path" "$APP_JNI_LIBS/$abi/mgba_libretro_android.so"
  echo "Packaged $APP_JNI_LIBS/$abi/mgba_libretro_android.so"
done

mkdir -p "$(dirname "$LICENSE_DEST")"
install -m 0644 "$SOURCE_DIR/LICENSE" "$LICENSE_DEST"
echo "Pinned mGBA core build complete for: ${ABIS[*]}"
