#!/usr/bin/env bash
# publish-r2.sh — build + upload the langbang APK to the alpacapps R2 bucket.
#
# What it does:
#   1. ./gradlew :app:assembleDebug          (bumps buildNumber, produces APK)
#   2. uploads as langbang-v{N}-arm64.apk     (pinned version, for rollback)
#   3. uploads as langbang-latest.apk         (stable pointer for the page)
#   4. patches rahulio/pages/langbang/index.html with the new build number
#   5. prints the public URLs
#
# This is the canonical release flow for langbang. We do NOT use GitHub Releases.
#
# Env / secrets: pulled from the Bitwarden "Cloudflare R2 — Object Storage" item
# in the DevOps-alpacapps collection. No keys live on disk.
#
# Usage:   ./scripts/publish-r2.sh
# Options: --skip-build       reuse the existing app/build APK
#          --no-page-edit     don't touch the langbang page

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

SKIP_BUILD=0
NO_PAGE_EDIT=0
for arg in "$@"; do
  case "$arg" in
    --skip-build)   SKIP_BUILD=1 ;;
    --no-page-edit) NO_PAGE_EDIT=1 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

# 1. Build.
if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "→ building debug APK"
  ./gradlew :app:assembleDebug -q
fi

APK="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }

BUILD_NUMBER=$(grep -oE 'buildNumber=[0-9]+' version.properties | cut -d= -f2)
VERSION_NAME=$(grep -oE 'versionName=[^[:space:]]+' version.properties | cut -d= -f2)
[ -n "$BUILD_NUMBER" ] || { echo "no buildNumber in version.properties" >&2; exit 1; }
FULL_VERSION="${VERSION_NAME}.${BUILD_NUMBER}"
echo "→ build $FULL_VERSION ($(du -h "$APK" | cut -f1))"

# 2. Pull R2 credentials from Bitwarden.
echo "→ unlocking Bitwarden"
if [ -z "${BW_SESSION:-}" ]; then
  export BW_SESSION="$(~/bin/bw-unlock)"
fi
ITEM=$(bw get item "Cloudflare R2 — Object Storage" --session "$BW_SESSION")
field() { jq -r --arg n "$1" '.fields[] | select(.name==$n) | .value' <<< "$ITEM"; }
export AWS_ACCESS_KEY_ID="$(field 'Access Key ID')"
export AWS_SECRET_ACCESS_KEY="$(field 'Secret Access Key')"
export AWS_DEFAULT_REGION=auto
ACCOUNT_ID="$(field 'Account ID')"
PUBLIC_BASE="$(field 'Public Dev URL')"
ENDPOINT="https://${ACCOUNT_ID}.r2.cloudflarestorage.com"
BUCKET="alpacapps"

# 3. Upload. The pinned file is uploaded first so latest.apk is never ahead of a
# missing v{N}.apk (the page references the pinned link too).
PINNED_KEY="langbang/langbang-v${BUILD_NUMBER}-arm64.apk"
LATEST_KEY="langbang/langbang-latest.apk"

up() {
  local key="$1"
  echo "→ uploading s3://${BUCKET}/${key}"
  aws s3 cp "$APK" "s3://${BUCKET}/${key}" \
    --endpoint-url "$ENDPOINT" \
    --content-type application/vnd.android.package-archive \
    --no-progress
}
up "$PINNED_KEY"
up "$LATEST_KEY"

# 4. Verify both URLs return 200.
verify() {
  local key="$1"
  local code
  code=$(curl -sI -o /dev/null -w '%{http_code}' "${PUBLIC_BASE}/${key}")
  if [ "$code" != "200" ]; then
    echo "  ✗ ${PUBLIC_BASE}/${key} → HTTP $code" >&2
    return 1
  fi
  echo "  ✓ ${PUBLIC_BASE}/${key}"
}
verify "$PINNED_KEY"
verify "$LATEST_KEY"

# 5. Patch the landing page to point at the new pinned build.
if [ "$NO_PAGE_EDIT" -eq 0 ]; then
  PAGE="$HOME/Documents/CodingProjects/genalpaca-admin/rahulio/pages/langbang/index.html"
  if [ -f "$PAGE" ]; then
    echo "→ patching page → v${BUILD_NUMBER}"
    # Two surgical replacements — label + href.
    sed -i.bak -E \
      -e "s|Current build — v[0-9.]+|Current build — v${FULL_VERSION}|" \
      -e "s|langbang-v[0-9]+-arm64\.apk|langbang-v${BUILD_NUMBER}-arm64.apk|g" \
      "$PAGE"
    rm -f "${PAGE}.bak"
    echo "  page diff (verify before committing):"
    (cd "$HOME/Documents/CodingProjects/genalpaca-admin" && git diff --stat rahulio/pages/langbang/index.html 2>/dev/null || true)
  else
    echo "  (page not found at $PAGE — skipping)"
  fi
fi

echo
echo "✓ published langbang ${FULL_VERSION}"
echo "  latest: ${PUBLIC_BASE}/${LATEST_KEY}"
echo "  pinned: ${PUBLIC_BASE}/${PINNED_KEY}"
