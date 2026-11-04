#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="v1.4.0"
ARCHIVE_NAME="melondsds_libretro-android-Release.zip"
ARCHIVE_URL="https://github.com/JesseTG/melonds-ds/releases/download/${VERSION}/${ARCHIVE_NAME}"
EXPECTED_SHA256="555568396dd5cf230030e4dd0ff0b0c6ca7118231fe562bc50680fefa7ad8594"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
ARCHIVE_PATH="${WORK_DIR}/${ARCHIVE_NAME}"

command -v curl >/dev/null
command -v sha256sum >/dev/null
command -v unzip >/dev/null
if command -v llvm-readelf >/dev/null; then
    READELF="$(command -v llvm-readelf)"
elif command -v readelf >/dev/null; then
    READELF="$(command -v readelf)"
else
    echo "readelf or llvm-readelf is required to validate the Android core ABI." >&2
    exit 1
fi

echo "Downloading melonDS DS ${VERSION} Android arm64 release..."
curl --fail --location --retry 3 --retry-delay 2 "${ARCHIVE_URL}" --output "${ARCHIVE_PATH}"
echo "${EXPECTED_SHA256}  ${ARCHIVE_PATH}" | sha256sum --check -

mapfile -t CORE_ENTRIES < <(unzip -Z1 "${ARCHIVE_PATH}" | grep -E '(^|/)melondsds_libretro_android\.so$' || true)
mapfile -t LICENSE_ENTRIES < <(unzip -Z1 "${ARCHIVE_PATH}" | grep -E '(^|/)melondsds-LICENSE\.txt$' || true)
mapfile -t INFO_ENTRIES < <(unzip -Z1 "${ARCHIVE_PATH}" | grep -E '(^|/)melondsds_libretro_android\.info$' || true)

if [[ "${#CORE_ENTRIES[@]}" -ne 1 ]]; then
    echo "Expected exactly one melondsds_libretro_android.so in the release archive." >&2
    printf '%s\n' "${CORE_ENTRIES[@]}" >&2
    exit 1
fi
if [[ "${#LICENSE_ENTRIES[@]}" -ne 1 ]]; then
    echo "Expected exactly one melonDS DS license file in the release archive." >&2
    exit 1
fi
if [[ "${#INFO_ENTRIES[@]}" -ne 1 ]]; then
    echo "Expected exactly one melonDS DS Libretro info file in the release archive." >&2
    exit 1
fi

CORE_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"
LICENSE_DIR="${ROOT_DIR}/app/src/main/assets/licenses"
INFO_DIR="${ROOT_DIR}/app/src/main/assets/libretro"
mkdir -p "${CORE_DIR}" "${LICENSE_DIR}" "${INFO_DIR}"

CORE_PATH="${CORE_DIR}/melondsds_libretro_android.so"
unzip -p "${ARCHIVE_PATH}" "${CORE_ENTRIES[0]}" > "${CORE_PATH}"
unzip -p "${ARCHIVE_PATH}" "${LICENSE_ENTRIES[0]}" > "${LICENSE_DIR}/melonds-ds-GPL-3.0.txt"
unzip -p "${ARCHIVE_PATH}" "${INFO_ENTRIES[0]}" > "${INFO_DIR}/melondsds_libretro_android.info"

MACHINE="$("${READELF}" -h "${CORE_PATH}" | sed -n 's/^[[:space:]]*Machine:[[:space:]]*//p')"
if [[ "${MACHINE}" != *AArch64* ]]; then
    echo "Unexpected melonDS DS Android core architecture: ${MACHINE}" >&2
    exit 1
fi

echo "Verified melonDS DS ${VERSION}: ${MACHINE}"
echo "Core dependencies:"
"${READELF}" -d "${CORE_PATH}" | grep 'NEEDED' || true
echo "Installed core at ${CORE_PATH}"
