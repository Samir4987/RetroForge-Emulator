#!/usr/bin/env bash
set -euo pipefail

echo "Starting cleanup for Android-only build..."

# Remove common unwanted directories
for dir in \
  desktop \
  web \
  desktopApp \
  webApp \
  composeApp \
  shared \
  ios \
  iosApp \
  macos \
  windows \
  wasm \
  .gradle \
  build \
  app/build \
  .idea \
  .kotlin
do
  if [ -e "$dir" ]; then
    rm -rf "$dir"
    echo "Deleted: $dir"
  fi
done

# Remove common generated local files
for file in \
  local.properties \
  .DS_Store
do
  if [ -e "$file" ]; then
    rm -f "$file"
    echo "Deleted: $file"
  fi
done

# Clean likely stale KMP project includes from settings.gradle.kts
if [ -f settings.gradle.kts ]; then
  python3 - <<'PY'
from pathlib import Path

p = Path("settings.gradle.kts")
if p.exists():
    text = p.read_text(encoding="utf-8")
    bad = [
        'include(":desktop")',
        'include(":web")',
        'include(":desktopApp")',
        'include(":webApp")',
        'include(":composeApp")',
        'include(":shared")',
        'include(":ios")',
        'include(":iosApp")',
        'include(":macos")',
        'include(":windows")',
        'include(":wasm")',
    ]
    for item in bad:
        text = text.replace(item, "")

    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith('include(":') and stripped.endswith('")'):
            if any(s in stripped for s in [':desktop', ':web', ':desktopApp', ':webApp', ':composeApp', ':shared', ':ios', ':iosApp', ':macos', ':windows', ':wasm']):
                continue
        lines.append(line)

    p.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print("Cleaned settings.gradle.kts")
PY
fi

echo "Cleanup complete."
