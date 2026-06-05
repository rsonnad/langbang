#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/identify-adb-tablets.sh
  scripts/identify-adb-tablets.sh --serial cream
  scripts/identify-adb-tablets.sh --serial blue
  scripts/identify-adb-tablets.sh --check

Classifies connected ADB devices by Android device name:
  * names containing "Cream Tab" are reported as cream
  * names containing "Blue Tab" are reported as blue

Use --serial to print the best current ADB route for a tablet alias.
USAGE
}

alias_from_name() {
  local name_lower
  name_lower="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "$name_lower" in
    *"cream tab"*) echo "cream" ;;
    *"blue tab"*) echo "blue" ;;
    *) echo "unknown" ;;
  esac
}

read_device_name() {
  local serial="$1"
  local name
  name="$(adb -s "$serial" shell settings get global device_name 2>/dev/null < /dev/null | tr -d '\r' | head -n 1 || true)"
  if [[ -z "$name" || "$name" == "null" ]]; then
    name="$(adb -s "$serial" shell settings get secure bluetooth_name 2>/dev/null < /dev/null | tr -d '\r' | head -n 1 || true)"
  fi
  if [[ -z "$name" || "$name" == "null" ]]; then
    name="unknown"
  fi
  echo "$name"
}

read_prop() {
  local serial="$1"
  local prop="$2"
  local value
  value="$(adb -s "$serial" shell getprop "$prop" 2>/dev/null < /dev/null | tr -d '\r' | head -n 1 || true)"
  if [[ -z "$value" ]]; then
    value="unknown"
  fi
  echo "$value"
}

route_score() {
  local alias="$1"
  local serial="$2"
  local score=0

  case "$serial" in
    *":5555") score=$((score + 50)) ;;
  esac
  case "$serial" in
    *:*) score=$((score + 30)) ;;
    adb-*) score=$((score + 20)) ;;
  esac

  case "$alias" in
    cream)
      case "$serial" in
        100.103.110.7:5555) score=1000 ;;
        100.103.110.7:*) score=900 ;;
        adb-R95Y301R05A*) score=800 ;;
      esac
      ;;
    blue)
      case "$serial" in
        100.85.223.100:5555) score=1000 ;;
        100.85.223.100:*) score=900 ;;
        adb-R5GL20C46YJ*) score=800 ;;
      esac
      ;;
  esac

  echo "$score"
}

collect_serials() {
  adb devices | awk 'NR > 1 && $2 == "device" {print $1}'
}

print_table() {
  local serial
  printf '%s\t%s\t%s\t%s\t%s\n' "alias" "adb_serial" "device_name" "model" "android_serial"
  while IFS= read -r serial; do
    [[ -n "$serial" ]] || continue
    local name alias model android_serial
    name="$(read_device_name "$serial")"
    alias="$(alias_from_name "$name")"
    model="$(read_prop "$serial" ro.product.model)"
    android_serial="$(read_prop "$serial" ro.serialno)"
    printf '%s\t%s\t%s\t%s\t%s\n' "$alias" "$serial" "$name" "$model" "$android_serial"
  done < <(collect_serials)
}

best_serial_for_alias() {
  local requested="$1"
  local serial best_serial="" best_score=-1
  while IFS= read -r serial; do
    [[ -n "$serial" ]] || continue
    local name alias score
    name="$(read_device_name "$serial")"
    alias="$(alias_from_name "$name")"
    if [[ "$alias" != "$requested" ]]; then
      continue
    fi
    score="$(route_score "$alias" "$serial")"
    if (( score > best_score )); then
      best_score="$score"
      best_serial="$serial"
    fi
  done < <(collect_serials)

  if [[ -z "$best_serial" ]]; then
    echo "No connected ADB device name matched '$requested'." >&2
    return 1
  fi
  echo "$best_serial"
}

check_expected_aliases() {
  local cream="" blue=""
  cream="$(best_serial_for_alias cream 2>/dev/null || true)"
  blue="$(best_serial_for_alias blue 2>/dev/null || true)"
  if [[ -z "$cream" || -z "$blue" ]]; then
    print_table >&2
    echo "Expected both Cream Tab and Blue Tab to be connected." >&2
    return 1
  fi
  printf 'cream\t%s\nblue\t%s\n' "$cream" "$blue"
}

case "${1:-}" in
  "")
    print_table
    ;;
  --serial)
    if [[ -z "${2:-}" || -n "${3:-}" ]]; then
      usage
      exit 2
    fi
    case "$2" in
      cream|blue) best_serial_for_alias "$2" ;;
      *)
        echo "Unknown tablet alias '$2'. Use cream or blue." >&2
        exit 2
        ;;
    esac
    ;;
  --check)
    if [[ -n "${2:-}" ]]; then
      usage
      exit 2
    fi
    check_expected_aliases
    ;;
  -h|--help)
    usage
    ;;
  *)
    usage
    exit 2
    ;;
esac
