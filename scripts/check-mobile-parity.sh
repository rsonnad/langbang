#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mode="${1:---static}"
case "$mode" in
  --static|--full) ;;
  *) echo "usage: $0 [--static|--full]" >&2; exit 2 ;;
esac

matrix="docs/process/mobile-parity-matrix.tsv"
android_shell="app/src/main/kotlin/com/sponic/langbang/ui/LangbangApp.kt"
ios_shell="iosApp/LangBang/ContentView.swift"

for required in "$matrix" "$android_shell" "$ios_shell"; do
  [ -f "$required" ] || { echo "missing parity input: $required" >&2; exit 1; }
done

awk -F '\t' '
  NF == 0 { next }
  NR == 1 {
    if ($0 != "feature_id\tandroid_surface\tshared_contract\tios_surface\tstatus\tevidence") {
      print "invalid parity matrix header" > "/dev/stderr"; exit 1
    }
    next
  }
  NF != 6 { print "invalid matrix row " NR ": expected 6 columns" > "/dev/stderr"; exit 1 }
  $5 !~ /^(android-ready|shared-ready|ios-visual|verified)$/ {
    print "invalid parity status on row " NR ": " $5 > "/dev/stderr"; exit 1
  }
  $5 == "verified" && ($3 == "-" || $6 == "-") {
    print "verified row lacks shared contract or evidence on row " NR > "/dev/stderr"; exit 1
  }
  { ids[$1]++ }
  END { for (id in ids) if (ids[id] > 1) { print "duplicate feature_id: " id > "/dev/stderr"; exit 1 } }
' "$matrix"

while IFS=$'\t' read -r feature android shared ios status evidence; do
  [ -z "$feature" ] && continue
  [ "$feature" = "feature_id" ] && continue
  [ -f "$android" ] || { echo "$feature: missing Android surface $android" >&2; exit 1; }
  [ -f "$ios" ] || { echo "$feature: missing iOS surface $ios" >&2; exit 1; }
  if [ "$shared" != "-" ]; then
    [ -f "$shared" ] || { echo "$feature: missing shared contract $shared" >&2; exit 1; }
  fi
done < "$matrix"

android_tabs="$(sed -n 's/^[[:space:]]*[A-Za-z][A-Za-z]*("[^"]*", "\([^"]*\)").*/\1/p' "$android_shell" | rg -v '^Settings$' | sort -u | tr '\n' ' ' | sed 's/ $//')"
ios_tabs="$(sed -n 's/^[[:space:]]*case [A-Za-z]* = "\([^"]*\)"/\1/p' "$ios_shell" | sort -u | tr '\n' ' ' | sed 's/ $//')"
if [ "$android_tabs" != "$ios_tabs" ]; then
  echo "mobile tab drift detected" >&2
  echo "Android: $android_tabs" >&2
  echo "iOS:     $ios_tabs" >&2
  exit 1
fi

rg -q 'NowVoicingPanel' "$android_shell" app/src/main/kotlin/com/sponic/langbang/ui/common/NowVoicingPanel.kt
rg -q 'NowVoicingPanel' "$ios_shell"
rg -q 'web' docs/process/mobile-parity.md
rg -q 'paused' docs/process/mobile-parity.md

echo "mobile parity static gate: OK"

if [ "$mode" = "--full" ]; then
  ./gradlew :shared:iosSimulatorArm64Test :shared:checkNoAndroidInCommon :shared:checkPracticeParity
  ./gradlew assembleEnPlDebug
  xcodebuild \
    -project iosApp/LangBang.xcodeproj \
    -scheme LangBang \
    -sdk iphonesimulator \
    -configuration Debug \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath /tmp/langbang-ios-parity-gate \
    ARCHS=arm64 \
    ONLY_ACTIVE_ARCH=YES \
    CODE_SIGNING_ALLOWED=NO \
    build
  echo "mobile parity full gate: OK"
fi
