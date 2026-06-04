#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

SKIP_BUILD=0
NO_SITE_DEPLOY=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --no-site-deploy) NO_SITE_DEPLOY=1 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

if [ "${LANGBANGML_SKIP_WORKTREE_AUDIT:-0}" != "1" ]; then
  scripts/check-worktree-integrity.sh
else
  scripts/check-tablet-regressions.sh
fi

PUBLIC_BASE="https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev"
API_BASE="https://langbangml-api.langbangml.workers.dev"
SITE_BUILDS_URL="https://langbang.org/builds"
SITE_DEPLOY_SCRIPT="/Users/rahulio/Documents/CodingProjects/langbang/scripts/deploy-langbang-org-site.sh"
BUCKET="langbangml"
R2_ITEM="Cloudflare R2 - LangBang S3 Admin"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

need aws
need bw
need jq
need curl

find_aapt2() {
  local sdk
  sdk=$(grep -oE '^sdk\.dir=.*' local.properties 2>/dev/null | cut -d= -f2 || true)
  [ -z "$sdk" ] && sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  find "$sdk/build-tools" -name aapt2 -type f 2>/dev/null | sort -V | tail -1
}

apk_info() {
  local apk="$1"
  if [ -n "${AAPT2:-}" ] && [ -x "$AAPT2" ]; then
    "$AAPT2" dump badging "$apk" 2>/dev/null
  else
    echo "aapt2 not found; cannot read APK metadata" >&2
    exit 1
  fi
}

field() {
  jq -r --arg n "$1" '.fields[]? | select(.name==$n) | .value' <<< "$BW_ITEM"
}

if [ "$SKIP_BUILD" -eq 0 ]; then
  ./gradlew --no-configuration-cache :app:assembleEnPlDebug :app:assemblePlEnDebug -q
fi

AAPT2="$(find_aapt2)"
[ -n "$AAPT2" ] || { echo "aapt2 not found in Android SDK build-tools" >&2; exit 1; }

BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null || true)"
[ -n "$BW_PASSWORD" ] || { echo "could not read Bitwarden CLI password from Keychain" >&2; exit 1; }
export BW_PASSWORD
BW_SESSION="$(bw unlock --passwordenv BW_PASSWORD --raw)"
export BW_SESSION
BW_ITEM="$(bw get item "$R2_ITEM" --session "$BW_SESSION")"

export AWS_ACCESS_KEY_ID="$(field "Access Key ID")"
export AWS_SECRET_ACCESS_KEY="$(field "Secret Access Key")"
export AWS_DEFAULT_REGION=auto
ENDPOINT="$(field "R2 Endpoint")"
ACCOUNT_ID="$(field "Account ID")"
[ -z "$ENDPOINT" ] && ENDPOINT="https://${ACCOUNT_ID}.r2.cloudflarestorage.com"

if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || [ -z "$ENDPOINT" ]; then
  echo "missing R2 S3 credentials in Bitwarden item: $R2_ITEM" >&2
  exit 1
fi

tmp_files=()
cleanup() {
  rm -f "${tmp_files[@]:-}"
}
trap cleanup EXIT

declare -a PAGE_ROWS=()

publish_channel() {
  local flavor="$1"
  local direction="$2"
  local instance_id="$3"
  local display_name="$4"
  local apk
  apk="$(find "app/build/outputs/apk/$flavor/debug" -name '*.apk' -type f | sort | head -1)"
  [ -f "$apk" ] || { echo "APK not found for flavor $flavor" >&2; exit 1; }

  local badging version_code full_version size_bytes pinned_key latest_key manifest_key pinned_url latest_url manifest_tmp
  badging="$(apk_info "$apk")"
  version_code="$(sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" <<< "$badging" | head -1)"
  full_version="$(sed -nE "s/.*versionName='([^']+)'.*/\1/p" <<< "$badging" | head -1)"
  size_bytes="$(stat -f%z "$apk")"
  [ -n "$version_code" ] && [ -n "$full_version" ] || {
    echo "could not read version metadata from $apk" >&2
    exit 1
  }

  pinned_key="langbang/builds/${direction}/langbangml-${direction}-v${version_code}-arm64.apk"
  latest_key="langbang/builds/${direction}/langbangml-${direction}-latest.apk"
  manifest_key="langbang/builds/${direction}/latest.json"
  pinned_url="${PUBLIC_BASE}/${pinned_key}"
  latest_url="${PUBLIC_BASE}/${latest_key}"

  echo "uploading ${display_name} ${full_version} (${version_code})"
  aws s3 cp "$apk" "s3://${BUCKET}/${pinned_key}" \
    --endpoint-url "$ENDPOINT" \
    --content-type application/vnd.android.package-archive \
    --no-progress
  aws s3 cp "$apk" "s3://${BUCKET}/${latest_key}" \
    --endpoint-url "$ENDPOINT" \
    --content-type application/vnd.android.package-archive \
    --no-progress

  manifest_tmp="$(mktemp -t langbangml-${direction}.XXXXXX.json)"
  tmp_files+=("$manifest_tmp")
  jq -n \
    --arg instanceId "$instance_id" \
    --arg displayName "$display_name" \
    --arg direction "$direction" \
    --arg versionName "$full_version" \
    --arg url "$latest_url" \
    --arg pinnedUrl "$pinned_url" \
    --arg apiBase "$API_BASE" \
    --arg publicR2Base "$PUBLIC_BASE" \
    --arg notes "LangBangML ${display_name} build published to the new Cloudflare account." \
    --argjson versionCode "$version_code" \
    --argjson sizeBytes "$size_bytes" \
    '{
      instanceId: $instanceId,
      displayName: $displayName,
      direction: $direction,
      versionCode: $versionCode,
      versionName: $versionName,
      url: $url,
      pinnedUrl: $pinnedUrl,
      sizeBytes: $sizeBytes,
      apiBase: $apiBase,
      publicR2Base: $publicR2Base,
      notes: $notes
    }' > "$manifest_tmp"
  aws s3 cp "$manifest_tmp" "s3://${BUCKET}/${manifest_key}" \
    --endpoint-url "$ENDPOINT" \
    --content-type application/json \
    --cache-control "no-cache, max-age=0" \
    --no-progress

  for url in "$pinned_url" "$latest_url" "${PUBLIC_BASE}/${manifest_key}"; do
    local code
    code="$(curl -sI -o /dev/null -w '%{http_code}' "${url}?verify=$(date +%s)")"
    [ "$code" = "200" ] || { echo "verify failed: $url -> HTTP $code" >&2; exit 1; }
  done

  PAGE_ROWS+=("${display_name}|v${version_code}|${full_version}|${latest_url}|${pinned_url}|${PUBLIC_BASE}/${manifest_key}")
}

publish_channel "enPl" "en-pl" "langbangml-en-pl" "English speakers learning Polish"
publish_channel "plEn" "pl-en" "langbangml-pl-en" "Polish speakers learning English"

page_tmp="$(mktemp -t langbangml-builds.XXXXXX.html)"
tmp_files+=("$page_tmp")
{
  cat <<'HTML'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>LangBangML builds</title>
  <style>
    body{margin:0;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#fff7f2;color:#1c1820;line-height:1.5}
    main{width:min(960px,calc(100vw - 32px));margin:0 auto;padding:44px 0 64px}
    h1{font-size:clamp(40px,7vw,72px);line-height:1;margin:0 0 12px}
    .tabs{display:flex;flex-wrap:wrap;gap:10px;margin:28px 0 18px}.tabs a{padding:10px 14px;border:1px solid rgba(45,31,48,.16);border-radius:8px;text-decoration:none;font-weight:800}
    section{padding:22px;border:1px solid rgba(45,31,48,.14);border-radius:8px;background:rgba(255,255,255,.7);margin:14px 0}
    .actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:18px}.button{padding:12px 16px;border-radius:8px;text-decoration:none;font-weight:800}.primary{background:#e74493;color:white}.secondary{border:1px solid rgba(45,31,48,.16);color:#1c1820}
    code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
  </style>
</head>
<body><main>
  <h1>LangBangML builds</h1>
  <p>Two APK channels from the new Cloudflare account. Each channel has its own latest APK, pinned APK, and update manifest.</p>
  <nav class="tabs"><a href="#en-pl">English to Polish</a><a href="#pl-en">Polish to English</a></nav>
HTML
  for row in "${PAGE_ROWS[@]}"; do
    IFS='|' read -r display version full latest pinned manifest <<< "$row"
    anchor="en-pl"
    case "$display" in
      Polish*) anchor="pl-en" ;;
    esac
    cat <<HTML
  <section id="$anchor">
    <h2>$display</h2>
    <p><strong>$version</strong> <code>$full</code></p>
    <div class="actions">
      <a class="button primary" href="$latest">Install latest</a>
      <a class="button secondary" href="$pinned">Pinned APK</a>
      <a class="button secondary" href="$manifest">Manifest JSON</a>
    </div>
  </section>
HTML
  done
  cat <<'HTML'
</main></body></html>
HTML
} > "$page_tmp"

aws s3 cp "$page_tmp" "s3://${BUCKET}/langbang/builds/index.html" \
  --endpoint-url "$ENDPOINT" \
  --content-type text/html \
  --cache-control "no-cache, max-age=0" \
  --no-progress
aws s3 cp "$page_tmp" "s3://${BUCKET}/langbang/builds/builds.html" \
  --endpoint-url "$ENDPOINT" \
  --content-type text/html \
  --cache-control "no-cache, max-age=0" \
  --no-progress

if [ "$NO_SITE_DEPLOY" -eq 0 ] && [ -x "$SITE_DEPLOY_SCRIPT" ]; then
  "$SITE_DEPLOY_SCRIPT"
fi

site_html="$(curl -fsS -H "Cache-Control: no-cache" "${SITE_BUILDS_URL}?verify=$(date +%s)")"
for row in "${PAGE_ROWS[@]}"; do
  IFS='|' read -r display version full latest pinned manifest <<< "$row"
  if ! grep -Fq "$latest" <<< "$site_html"; then
    echo "live builds page is missing latest URL for ${display}: ${latest}" >&2
    exit 1
  fi
  if ! grep -Fq "$pinned" <<< "$site_html"; then
    echo "live builds page is missing pinned URL for ${display}: ${pinned}" >&2
    exit 1
  fi
done

echo
echo "published LangBangML build channels:"
for row in "${PAGE_ROWS[@]}"; do
  IFS='|' read -r display version full latest pinned manifest <<< "$row"
  echo "  ${display}: ${version} ${latest}"
done
echo "  builds page: ${PUBLIC_BASE}/langbang/builds/index.html"
echo "  live site: ${SITE_BUILDS_URL}"
