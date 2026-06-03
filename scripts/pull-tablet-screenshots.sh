#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-4}"
DEST="${2:-${TAB_SCREENSHOT_DEST_ROOT:-$HOME/Documents/Screenshotz}/TabA9-$(date +%Y%m%d-%H%M%S)}"

if ! [[ "$COUNT" =~ ^[0-9]+$ ]] || [ "$COUNT" -lt 1 ]; then
  echo "Usage: $0 [count] [destination-dir]" >&2
  exit 2
fi

find_tablet_serial() {
  if [ -n "${ADB_SERIAL:-}" ]; then
    echo "$ADB_SERIAL"
    return 0
  fi

  local fixed="100.103.110.7:5555"
  if adb devices | awk '{print $1}' | grep -qx "$fixed"; then
    echo "$fixed"
    return 0
  fi

  local serial
  serial="$(adb devices -l | awk '/model:SM_X210|model:SM-X210|gta9/ {print $1; exit}')"
  if [ -n "$serial" ]; then
    echo "$serial"
    return 0
  fi

  if command -v adb-tab >/dev/null 2>&1; then
    adb-tab >/dev/null 2>&1 || true
    serial="$(adb devices -l | awk '/model:SM_X210|model:SM-X210|gta9/ {print $1; exit}')"
    if [ -n "$serial" ]; then
      echo "$serial"
      return 0
    fi
  fi

  return 1
}

SERIAL="$(find_tablet_serial)" || {
  echo "No connected Galaxy Tab A9+ ADB serial found." >&2
  exit 1
}

SOURCE_DIR=""
for dir in /sdcard/DCIM/Screenshots /sdcard/Pictures/Screenshots; do
  if adb -s "$SERIAL" shell "[ -d '$dir' ]" >/dev/null 2>&1; then
    if [ -n "$(adb -s "$SERIAL" shell "ls -t '$dir' 2>/dev/null | head -1" | tr -d '\r')" ]; then
      SOURCE_DIR="$dir"
      break
    fi
  fi
done

if [ -z "$SOURCE_DIR" ]; then
  echo "No tablet screenshot files found under DCIM/Screenshots or Pictures/Screenshots." >&2
  exit 1
fi

mkdir -p "$DEST"

adb -s "$SERIAL" shell "ls -t '$SOURCE_DIR' 2>/dev/null | head -$COUNT" |
  tr -d '\r' |
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    adb -s "$SERIAL" pull "$SOURCE_DIR/$name" "$DEST/" >/dev/null
  done

echo "$DEST"
