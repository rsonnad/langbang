#!/usr/bin/env bash
set -euo pipefail

SERIAL="${ADB_SERIAL:-100.103.110.7:5555}"
PACKAGE="${LANGBANGML_PACKAGE:-com.sponic.langbangml.enpl}"
RECEIVER="com.sponic.langbang.ExternalNowVoicingReceiver"
ACTION="com.sponic.langbangml.DRIVE_NOW_VOICING"
SPEAKER="top"
DIALOG=0
FOCUS=1
MAROON="bottom"
LANGUAGE="pl"
EN=""
PL=""
LITERAL=""
POSITION=""
WORDS=""
CLEAR=0

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/drive-now-voicing.sh [--serial SERIAL] [--package PACKAGE] [--dialog]
    [--speaker top|bottom] [--maroon top|bottom] [--lang en|pl|pl-slow|pause]
    --en TEXT --pl TEXT [--literal TEXT] [--words JSON] [--position TEXT]

  scripts/drive-now-voicing.sh --clear
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --speaker) SPEAKER="$2"; shift 2 ;;
    --maroon) MAROON="$2"; shift 2 ;;
    --lang) LANGUAGE="$2"; shift 2 ;;
    --en) EN="$2"; shift 2 ;;
    --pl) PL="$2"; shift 2 ;;
    --literal) LITERAL="$2"; shift 2 ;;
    --words) WORDS="$2"; shift 2 ;;
    --position) POSITION="$2"; shift 2 ;;
    --dialog) DIALOG=1; shift ;;
    --no-focus) FOCUS=0; shift ;;
    --clear) CLEAR=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

shell_quote() {
  printf "'%s'" "$(printf "%s" "$1" | sed "s/'/'\\\\''/g")"
}

cmd="am broadcast -a $(shell_quote "$ACTION") -n $(shell_quote "${PACKAGE}/${RECEIVER}")"
if [ "$CLEAR" -eq 1 ]; then
  cmd="$cmd --ez clear true"
else
  cmd="$cmd --ez dialog $([ "$DIALOG" -eq 1 ] && echo true || echo false)"
  cmd="$cmd --ez focus $([ "$FOCUS" -eq 1 ] && echo true || echo false)"
  cmd="$cmd --es speaker $(shell_quote "$SPEAKER")"
  cmd="$cmd --es maroon $(shell_quote "$MAROON")"
  cmd="$cmd --es lang $(shell_quote "$LANGUAGE")"
  cmd="$cmd --es en $(shell_quote "$EN")"
  cmd="$cmd --es pl $(shell_quote "$PL")"
  [ -n "$LITERAL" ] && cmd="$cmd --es literal $(shell_quote "$LITERAL")"
  [ -n "$WORDS" ] && cmd="$cmd --es words $(shell_quote "$WORDS")"
  [ -n "$POSITION" ] && cmd="$cmd --es position $(shell_quote "$POSITION")"
fi

adb -s "$SERIAL" shell "$cmd"
