#!/usr/bin/env bash
# publish-r2.sh — build + upload the langbang APK to the alpacapps R2 bucket.
#
# What it does:
#   1. ./gradlew :app:assembleDebug          (bumps buildNumber, produces APK)
#   2. uploads as langbang-v{N}-arm64.apk     (pinned version, for rollback)
#   3. uploads as langbang-latest.apk         (stable pointer for the page)
#   4. patches rahulio/pages/langbang/index.html with the new build number
#   5. commits + pushes JUST that page change in the alpacapps repo
#      (so the live label can't drift behind R2 — see 2026-05-25 incident
#      where the page sat at v45 while R2 was on v68)
#   6. prints the public URLs
#
# This is the canonical release flow for langbang. We do NOT use GitHub Releases.
#
# Env / secrets: pulled from the Bitwarden "Cloudflare R2 — Object Storage" item
# in the DevOps-alpacapps collection. No keys live on disk.
#
# Usage:   ./scripts/publish-r2.sh
# Options: --skip-build       reuse the existing app/build APK
#          --no-page-edit     don't touch the langbang page
#          --no-page-push     edit the page locally but don't commit/push

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

LOCK_DIR="${TMPDIR:-/tmp}/langbang-publish-r2.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  LOCK_PID="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  if [ -n "$LOCK_PID" ] && kill -0 "$LOCK_PID" 2>/dev/null; then
    echo "another publish-r2.sh is already running (pid $LOCK_PID)" >&2
    exit 1
  fi
  rm -rf "$LOCK_DIR"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "another publish-r2.sh started while acquiring the lock" >&2
    exit 1
  fi
fi
echo "$$" > "$LOCK_DIR/pid"
trap 'rm -rf "$LOCK_DIR"' EXIT

SKIP_BUILD=0
NO_PAGE_EDIT=0
NO_PAGE_PUSH=0
for arg in "$@"; do
  case "$arg" in
    --skip-build)   SKIP_BUILD=1 ;;
    --no-page-edit) NO_PAGE_EDIT=1 ;;
    --no-page-push) NO_PAGE_PUSH=1 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

# 1. Build.
if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "→ building debug APK"
  ./gradlew :app:assembleDebug -q
fi

APK="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
# The APK is the source of truth for the version — NOT version.properties. With
# Gradle config-caching on and a mutable buildNumber counter, the number baked into
# the APK can lag what's written to version.properties (especially when two build
# sessions race the file). Reading from the APK via aapt2 guarantees the pinned R2
# filename, the page label, and the in-app "version" header can never disagree.
# (2026-05-30 incident: version.properties said 87 while a freshly-built APK reported
# 85 — the published filename lied. Reading from the APK eliminates the whole class.)
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }

find_aapt2() {
  local sdk
  sdk=$(grep -oE '^sdk\.dir=.*' local.properties 2>/dev/null | cut -d= -f2)
  [ -z "$sdk" ] && sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  ls "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1
}
AAPT2="$(find_aapt2)"
if [ -n "$AAPT2" ] && [ -x "$AAPT2" ]; then
  BADGING="$("$AAPT2" dump badging "$APK" 2>/dev/null)"
  BUILD_NUMBER=$(sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" <<< "$BADGING" | head -1)
  FULL_VERSION=$(sed -nE "s/.*versionName='([^']+)'.*/\1/p" <<< "$BADGING" | head -1)
  VERSION_NAME="${FULL_VERSION%.*}"   # strip the trailing .build suffix for display
  echo "→ version read from APK (aapt2): $FULL_VERSION (code $BUILD_NUMBER)"
else
  # Fallback: parse version.properties (less reliable — see note above).
  BUILD_NUMBER=$(grep -oE 'buildNumber=[0-9]+' version.properties | cut -d= -f2)
  VERSION_NAME=$(grep -oE 'versionName=[^[:space:]]+' version.properties | cut -d= -f2)
  FULL_VERSION="${VERSION_NAME}.${BUILD_NUMBER}"
  echo "→ aapt2 not found; version read from version.properties: $FULL_VERSION" >&2
fi
[ -n "$BUILD_NUMBER" ] || { echo "could not determine build number" >&2; exit 1; }
echo "→ build $FULL_VERSION ($(du -h "$APK" | cut -f1))"

# 2. Pull R2 credentials from Bitwarden.
echo "→ unlocking Bitwarden"
BW_STATUS=""
if [ -n "${BW_SESSION:-}" ]; then
  BW_STATUS="$(bw status --session "$BW_SESSION" 2>/dev/null | jq -r '.status // empty' || true)"
fi
if [ "$BW_STATUS" != "unlocked" ]; then
  export BW_SESSION="$(~/bin/bw-unlock)"
fi
ITEM=$(bw get item "Cloudflare R2 — Object Storage" --session "$BW_SESSION")
field() { jq -r --arg n "$1" '.fields[] | select(.name==$n) | .value' <<< "$ITEM"; }
export AWS_ACCESS_KEY_ID="$(field 'Access Key ID')"
export AWS_SECRET_ACCESS_KEY="$(field 'Secret Access Key')"
export AWS_DEFAULT_REGION=auto
ACCOUNT_ID="$(field 'Account ID')"
PUBLIC_BASE="$(field 'Public Dev URL')"
if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] ||
   [ -z "$ACCOUNT_ID" ] || [ -z "$PUBLIC_BASE" ]; then
  echo "missing required Cloudflare R2 fields from Bitwarden item" >&2
  exit 1
fi
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

# 3b. Write the update manifest the in-app UpdateChecker polls on launch. versionCode
# is read from the APK (see above) so the app's "is there a newer build" comparison
# can never be fooled by a stale version.properties. no-cache so a fresh publish is
# seen promptly past R2's edge cache.
MANIFEST_KEY="langbang/langbang-latest.json"
MANIFEST_TMP="$(mktemp -t langbang-manifest.XXXXXX.json)"
cat > "$MANIFEST_TMP" <<JSON
{"versionCode": ${BUILD_NUMBER}, "versionName": "${FULL_VERSION}", "url": "${PUBLIC_BASE}/${LATEST_KEY}"}
JSON
echo "→ uploading s3://${BUCKET}/${MANIFEST_KEY} (versionCode ${BUILD_NUMBER})"
aws s3 cp "$MANIFEST_TMP" "s3://${BUCKET}/${MANIFEST_KEY}" \
  --endpoint-url "$ENDPOINT" \
  --content-type application/json \
  --cache-control "no-cache, max-age=0" \
  --no-progress
rm -f "$MANIFEST_TMP"

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
verify "$MANIFEST_KEY"
verify "$PINNED_KEY"
verify "$LATEST_KEY"

# The update checker trusts langbang-latest.json, so verify the public manifest
# body too, not just the HTTP status. This catches edge-cache or concurrent-publish
# drift before the page is patched or devices are verified against the wrong build.
MANIFEST_PUBLIC="$(curl -fsS -H 'Cache-Control: no-cache' "${PUBLIC_BASE}/${MANIFEST_KEY}?verify=$(date +%s)")"
MANIFEST_CODE="$(jq -r '.versionCode // empty' <<< "$MANIFEST_PUBLIC")"
MANIFEST_NAME="$(jq -r '.versionName // empty' <<< "$MANIFEST_PUBLIC")"
if [ "$MANIFEST_CODE" != "$BUILD_NUMBER" ] || [ "$MANIFEST_NAME" != "$FULL_VERSION" ]; then
  echo "  ✗ public manifest mismatch: expected ${FULL_VERSION} (${BUILD_NUMBER}), got ${MANIFEST_NAME} (${MANIFEST_CODE})" >&2
  exit 1
fi
echo "  ✓ manifest version ${MANIFEST_NAME} (${MANIFEST_CODE})"

# 5. Patch the landing page to point at the new pinned build, then commit + push
#    JUST that file. Stages only the page (never `git add .`) so unrelated WIP in
#    the alpacapps tree stays untouched. Rebase-then-push handles concurrent
#    pushes from other tenants of that repo.
if [ "$NO_PAGE_EDIT" -eq 0 ]; then
  ALPACAPPS="$HOME/Documents/CodingProjects/genalpaca-admin"
  PAGE_REL="rahulio/pages/langbang/index.html"
  PAGE="$ALPACAPPS/$PAGE_REL"
  if [ -f "$PAGE" ]; then
    echo "→ patching page → v${FULL_VERSION}"
    # Two surgical replacements — label + href.
    sed -i.bak -E \
      -e "s|Current build — v[0-9.]+|Current build — v${FULL_VERSION}|" \
      -e "s|langbang-v[0-9]+-arm64\.apk|langbang-v${BUILD_NUMBER}-arm64.apk|g" \
      "$PAGE"
    rm -f "${PAGE}.bak"

    if [ "$NO_PAGE_PUSH" -eq 1 ]; then
      echo "  page diff (--no-page-push set; not committing):"
      (cd "$ALPACAPPS" && git diff --stat "$PAGE_REL" 2>/dev/null || true)
    else
      # Commit only if the page actually changed (idempotent re-runs are a no-op).
      if (cd "$ALPACAPPS" && ! git diff --quiet -- "$PAGE_REL"); then
        echo "→ committing + pushing page bump in alpacapps"
        (
          cd "$ALPACAPPS"
          git add "$PAGE_REL"
          git commit -m "langbang page: pinned APK → v${BUILD_NUMBER}" >/dev/null
          # Rebase any concurrent commits, then push. If rebase fails, leave the
          # commit local and tell the operator — don't risk a force-push.
          if git pull --rebase --autostash >/dev/null 2>&1; then
            if git push >/dev/null 2>&1; then
              echo "  ✓ pushed → live page will update in ~30s via Cloudflare Pages"
            else
              echo "  ✗ git push failed — page commit is local at $(git rev-parse --short HEAD)" >&2
              exit 1
            fi
          else
            echo "  ✗ git pull --rebase failed in $ALPACAPPS — resolve manually and push" >&2
            exit 1
          fi
        )
      else
        echo "  page already up to date (no commit)"
      fi
    fi
  else
    echo "  (page not found at $PAGE — skipping)"
  fi
fi

echo
echo "✓ published langbang ${FULL_VERSION}"
echo "  latest: ${PUBLIC_BASE}/${LATEST_KEY}"
echo "  pinned: ${PUBLIC_BASE}/${PINNED_KEY}"
