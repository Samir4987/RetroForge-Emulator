#!/usr/bin/env bash
set -euo pipefail

# Build the official Mupen64Plus-Next Android GLES3 Libretro core.
# Upstream Android.mk builds the module as libretro.so; package it under the
# filename consumed by LibretroCoreSpec. No ROMs or BIOS files are included.
readonly UPSTREAM_URL="https://github.com/libretro/mupen64plus-libretro-nx.git"
readonly UPSTREAM_REVISION="6752836de8b224febfd5708444755b77712ac939"
readonly ANDROID_MIN_API="24"
readonly ABI="${N64_ANDROID_ABI:-arm64-v8a}"

case "$ABI" in
  arm64-v8a|armeabi-v7a|x86|x86_64) ;;
  *) echo "Unsupported N64 Android ABI: $ABI" >&2; exit 2 ;;
esac

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" ]]; then
  echo "Set ANDROID_SDK_ROOT (or ANDROID_HOME) before building Mupen64Plus-Next." >&2
  exit 1
fi

ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-}"
if [[ -z "$ANDROID_NDK_ROOT" ]]; then
  if [[ -d "$ANDROID_SDK_ROOT/ndk/27.2.12479018" ]]; then
    ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
  else
    shopt -s nullglob
    ndks=("$ANDROID_SDK_ROOT"/ndk/*)
    shopt -u nullglob
    if (( ${#ndks[@]} == 0 )); then
      echo "No Android NDK found under $ANDROID_SDK_ROOT/ndk." >&2
      exit 1
    fi
    ANDROID_NDK_ROOT="${ndks[0]}"
  fi
fi

NDK_BUILD="$ANDROID_NDK_ROOT/ndk-build"
if [[ ! -x "$NDK_BUILD" ]]; then
  echo "ndk-build not found at $NDK_BUILD" >&2
  exit 1
fi

WORK_ROOT="${N64_BUILD_ROOT:-${RUNNER_TEMP:-/tmp}/retroforge-mupen64plus-build}"
SOURCE_DIR="$WORK_ROOT/source"
OUTPUT_DIR="$WORK_ROOT/ndk-out"
LIBS_DIR="$WORK_ROOT/ndk-libs"
APP_JNI_LIBS="${GITHUB_WORKSPACE:-$(pwd)}/app/src/main/jniLibs"
LICENSE_DEST="${GITHUB_WORKSPACE:-$(pwd)}/app/src/main/assets/licenses/mupen64plus-next-GPL-3.0.txt"
mkdir -p "$WORK_ROOT"

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
  git clone --filter=blob:none "$UPSTREAM_URL" "$SOURCE_DIR"
fi
git -C "$SOURCE_DIR" fetch --depth=1 origin "$UPSTREAM_REVISION"
git -C "$SOURCE_DIR" checkout --detach "$UPSTREAM_REVISION"

actual_revision="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
if [[ "$actual_revision" != "$UPSTREAM_REVISION" ]]; then
  echo "Mupen64Plus-Next revision mismatch: expected $UPSTREAM_REVISION, got $actual_revision" >&2
  exit 1
fi

test -s "$SOURCE_DIR/libretro/jni/Android.mk"
test -s "$SOURCE_DIR/libretro/jni/Application.mk"
test -s "$SOURCE_DIR/LICENSE"

echo "Building Mupen64Plus-Next GLES3 for $ABI at $UPSTREAM_REVISION"
export NDK_ROOT="$ANDROID_NDK_ROOT"
"$NDK_BUILD" \
  NDK_PROJECT_PATH="$SOURCE_DIR" \
  APP_BUILD_SCRIPT="$SOURCE_DIR/libretro/jni/Android.mk" \
  NDK_APPLICATION_MK="$SOURCE_DIR/libretro/jni/Application.mk" \
  NDK_OUT="$OUTPUT_DIR" \
  NDK_LIBS_OUT="$LIBS_DIR" \
  APP_ABI="$ABI" \
  APP_PLATFORM="android-$ANDROID_MIN_API" \
  APP_SHORT_COMMANDS=true \
  GLES3=1 \
  HAVE_PARALLEL_RSP=1 \
  HAVE_PARALLEL_RDP=1 \
  HAVE_THR_AL=1 \
  LLE=1 \
  -j2

CORE_SOURCE="$LIBS_DIR/$ABI/libretro.so"
if [[ ! -s "$CORE_SOURCE" ]]; then
  echo "Expected N64 core output was not produced: $CORE_SOURCE" >&2
  find "$LIBS_DIR" -type f -name '*.so' -print >&2 || true
  exit 1
fi

mkdir -p "$APP_JNI_LIBS/$ABI" "$(dirname "$LICENSE_DEST")"
install -m 0644 "$CORE_SOURCE" "$APP_JNI_LIBS/$ABI/mupen64plus_next_libretro_android.so"
install -m 0644 "$SOURCE_DIR/LICENSE" "$LICENSE_DEST"

echo "Packaged $APP_JNI_LIBS/$ABI/mupen64plus_next_libretro_android.so"
echo "Mupen64Plus-Next GLES3 build complete for $ABI."
