#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

SKIP_BUILD=0
NO_SITE_DEPLOY=0
ONLY_CHANNEL=""
ALLOW_CURRENT_DIRTY=0
ALLOW_UNRELATED_BRANCH_GAPS=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --no-site-deploy) NO_SITE_DEPLOY=1 ;;
    --allow-current-dirty) ALLOW_CURRENT_DIRTY=1 ;;
    --allow-unrelated-branch-gaps) ALLOW_UNRELATED_BRANCH_GAPS=1 ;;
    --only=*) ONLY_CHANNEL="${arg#--only=}" ;;
    --only)
      echo "--only requires a value: en-pl, pl-en, en-ja, or g2trans" >&2
      exit 2
      ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

case "$ONLY_CHANNEL" in
  ""|"en-pl"|"pl-en"|"en-ja"|"g2trans") ;;
  *) echo "--only must be en-pl, pl-en, en-ja, or g2trans" >&2; exit 2 ;;
esac

if [ "${LANGBANGML_SKIP_WORKTREE_AUDIT:-0}" != "1" ]; then
  audit_args=()
  [ "$ALLOW_CURRENT_DIRTY" -eq 1 ] && audit_args+=(--allow-current-dirty)
  [ "$ALLOW_UNRELATED_BRANCH_GAPS" -eq 1 ] && audit_args+=(--allow-unrelated-branch-gaps)
  scripts/check-worktree-integrity.sh "${audit_args[@]}"
else
  scripts/check-tablet-regressions.sh
fi

PUBLIC_BASE="https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev"
API_BASE="https://langbangml-api.langbangml.workers.dev"
SITE_BUILDS_URL="https://langbang.org/builds"
SITE_DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy-langbang-org-site.sh"
WRANGLER_CONFIG="$REPO_ROOT/cloudflare/langbangml/wrangler.toml"
BUCKET="langbangml"
R2_ITEM="Cloudflare R2 - LangBang S3 Admin"
CF_ITEM="Cloudflare - LangBang Codex Claude Admin"

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
need wrangler
need python3

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

g2trans_bump_patch_version() {
  local version_file="$REPO_ROOT/g2trans/version.properties"
  local version_name version_code major minor patch
  [ -f "$version_file" ] || {
    printf 'versionName=0.1.0\nversionCode=1\n' > "$version_file"
  }
  version_name="$(sed -nE 's/^versionName=([0-9]+\.[0-9]+\.[0-9]+)$/\1/p' "$version_file" | head -1)"
  version_code="$(sed -nE 's/^versionCode=([0-9]+)$/\1/p' "$version_file" | head -1)"
  [ -n "$version_name" ] && [ -n "$version_code" ] || {
    echo "invalid g2trans version file: $version_file" >&2
    exit 1
  }
  IFS=. read -r major minor patch <<< "$version_name"
  version_name="${major}.${minor}.$((patch + 1))"
  version_code="$((version_code + 1))"
  printf 'versionName=%s\nversionCode=%s\n' "$version_name" "$version_code" > "$version_file"
  echo "bumped g2trans to ${version_name} (${version_code})"
}

if [ "$SKIP_BUILD" -eq 0 ]; then
  if [ "${LANGBANGTRANS_SKIP_VERSION_BUMP:-0}" != "1" ] &&
    { [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "g2trans" ]; }; then
    g2trans_bump_patch_version
  fi
  case "$ONLY_CHANNEL" in
    en-pl) ./gradlew --no-configuration-cache :app:assembleEnPlDebug -q ;;
    pl-en) ./gradlew --no-configuration-cache :app:assemblePlEnDebug -q ;;
    en-ja) ./gradlew --no-configuration-cache :app:assembleEnJaDebug -q ;;
    g2trans) ./gradlew --no-configuration-cache :g2trans:assembleDebug -q ;;
    *) ./gradlew --no-configuration-cache :app:assembleEnPlDebug :app:assemblePlEnDebug :app:assembleEnJaDebug :g2trans:assembleDebug -q ;;
  esac
fi

AAPT2="$(find_aapt2)"
[ -n "$AAPT2" ] || { echo "aapt2 not found in Android SDK build-tools" >&2; exit 1; }

verify_en_ja_content_and_audio() {
  local bootstrap
  bootstrap="$(curl -fsS "${API_BASE}/v1/instances/langbangml-en-ja/bootstrap")"
  jq -e '
    .instance.id == "langbangml-en-ja" and
    .content.versionId == "en-ja-v1" and
    .languagePair.sourceLocale == "en-US" and
    .languagePair.targetLocale == "ja-JP" and
    .languagePair.targetVoice == "ja-JP-NanamiNeural" and
    ([.content.lessons[] | select(.type == "verbs") | .payload.verbs[]] | length) == 10 and
    ([.content.lessons[] | select(.type == "adjectives") | .payload.adjectives[]] | length) == 10 and
    ([.content.lessons[] | select(.type == "nouns") | .payload.nouns[]] | length) == 10 and
    ([.content.lessons[] | select(.type == "phrases") | .payload.groups[].sentences[]] | length) == 25
  ' >/dev/null <<<"$bootstrap" || {
    echo "EN-JA Cloudflare content preflight failed; apply its D1 migration before publishing." >&2
    exit 1
  }
  echo "warming and verifying EN-JA Worker/R2 audio"
  python3 scripts/populate-langbangml-audio.py --instance langbangml-en-ja
}

if [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "en-ja" ]; then
  verify_en_ja_content_and_audio
fi

if [ -z "${BW_SESSION:-}" ] || ! bw status --session "$BW_SESSION" </dev/null 2>/dev/null | jq -e '.status == "unlocked"' >/dev/null; then
  BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null || true)"
  [ -n "$BW_PASSWORD" ] || { echo "could not read Bitwarden CLI password from Keychain" >&2; exit 1; }
  export BW_PASSWORD
  BW_SESSION="$(/opt/homebrew/bin/bw unlock --passwordenv BW_PASSWORD --raw </dev/null)"
  unset BW_PASSWORD
  export BW_SESSION
fi
BW_ITEM="$(bw get item "$R2_ITEM" --session "$BW_SESSION" </dev/null)"
CF_ITEM_JSON="$(bw get item "$CF_ITEM" --session "$BW_SESSION" </dev/null 2>/dev/null || true)"
CF_API_TOKEN=""
if [ -n "$CF_ITEM_JSON" ]; then
  CF_API_TOKEN="$(jq -r '.fields[]? | select(.name == "Cloudflare Account API Token") | .value // empty' <<< "$CF_ITEM_JSON")"
fi

export AWS_ACCESS_KEY_ID="$(field "Access Key ID")"
export AWS_SECRET_ACCESS_KEY="$(field "Secret Access Key")"
export AWS_DEFAULT_REGION=auto
export AWS_CLI_FILE_TRANSFER_MAX_CONCURRENCY="${AWS_CLI_FILE_TRANSFER_MAX_CONCURRENCY:-1}"
export AWS_CLI_FILE_TRANSFER_MULTIPART_THRESHOLD="${AWS_CLI_FILE_TRANSFER_MULTIPART_THRESHOLD:-128MB}"
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

s3_put() {
  local source="$1"
  local key="$2"
  local content_type="$3"
  local cache_control="${4:-}"
  if [ -n "$CF_API_TOKEN" ] && [ -n "$ACCOUNT_ID" ]; then
    local response_tmp curl_config_tmp
    response_tmp="$(mktemp -t langbangml-r2-put.XXXXXX.json)"
    curl_config_tmp="$(mktemp -t langbangml-r2-put.XXXXXX.curl)"
    tmp_files+=("$response_tmp" "$curl_config_tmp")
    {
      printf 'fail\n'
      printf 'show-error\n'
      printf 'retry = 3\n'
      printf 'retry-all-errors\n'
      printf 'connect-timeout = 20\n'
      printf 'max-time = 600\n'
      printf 'request = "PUT"\n'
      printf 'header = "Authorization: Bearer %s"\n' "$CF_API_TOKEN"
      printf 'header = "Content-Type: %s"\n' "$content_type"
      if [ -n "$cache_control" ]; then
        printf 'header = "Cache-Control: %s"\n' "$cache_control"
      fi
      printf 'data-binary = "@%s"\n' "$source"
      printf 'output = "%s"\n' "$response_tmp"
      printf 'url = "https://api.cloudflare.com/client/v4/accounts/%s/r2/buckets/%s/objects/%s"\n' \
        "$ACCOUNT_ID" "$BUCKET" "$key"
    } > "$curl_config_tmp"
    if curl --config "$curl_config_tmp" && jq -e '.success == true' "$response_tmp" >/dev/null; then
      return
    fi
    echo "Cloudflare API upload failed for ${key}; trying wrangler/S3 fallback" >&2
  fi
  if [ -n "$CF_API_TOKEN" ]; then
    local wrangler_args=(
      r2 object put "${BUCKET}/${key}"
      --file "$source"
      --content-type "$content_type"
      --config "$WRANGLER_CONFIG"
      --remote
    )
    if [ -n "$cache_control" ]; then
      wrangler_args+=(--cache-control "$cache_control")
    fi
    if env CLOUDFLARE_API_TOKEN="$CF_API_TOKEN" wrangler "${wrangler_args[@]}" >/dev/null; then
      return
    fi
    echo "wrangler upload failed for ${key}; falling back to S3 API" >&2
  fi
  local args=(
    s3api put-object
    --bucket "$BUCKET"
    --key "$key"
    --body "$source"
    --endpoint-url "$ENDPOINT"
    --content-type "$content_type"
  )
  if [ -n "$cache_control" ]; then
    args+=(--cache-control "$cache_control")
  fi
  aws "${args[@]}" >/dev/null
}

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
  s3_put "$apk" "$pinned_key" "application/vnd.android.package-archive"
  s3_put "$apk" "$latest_key" "application/vnd.android.package-archive"

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
  s3_put "$manifest_tmp" "$manifest_key" "application/json" "no-cache, max-age=0"

  for url in "$pinned_url" "$latest_url" "${PUBLIC_BASE}/${manifest_key}"; do
    local code
    code="$(curl -sI -o /dev/null -w '%{http_code}' "${url}?verify=$(date +%s)")"
    [ "$code" = "200" ] || { echo "verify failed: $url -> HTTP $code" >&2; exit 1; }
  done

  PAGE_ROWS+=("${display_name}|v${version_code}|${full_version}|${latest_url}|${pinned_url}|${PUBLIC_BASE}/${manifest_key}")
}

publish_g2trans_channel() {
  local apk="g2trans/build/outputs/apk/debug/g2trans-debug.apk"
  [ -f "$apk" ] || { echo "APK not found for g2trans" >&2; exit 1; }

  local badging version_code full_version size_bytes pinned_key latest_key manifest_key pinned_url latest_url manifest_tmp
  badging="$(apk_info "$apk")"
  version_code="$(sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" <<< "$badging" | head -1)"
  full_version="$(sed -nE "s/.*versionName='([^']+)'.*/\1/p" <<< "$badging" | head -1)"
  size_bytes="$(stat -f%z "$apk")"
  [ -n "$version_code" ] && [ -n "$full_version" ] || {
    echo "could not read version metadata from $apk" >&2
    exit 1
  }

  pinned_key="langbang/builds/g2trans/langbangtrans-v${version_code}.apk"
  latest_key="langbang/builds/g2trans/langbangtrans-latest.apk"
  manifest_key="langbang/builds/g2trans/latest.json"
  pinned_url="${PUBLIC_BASE}/${pinned_key}"
  latest_url="${PUBLIC_BASE}/${latest_key}"

  echo "uploading LangBangTrans G2 Translate ${full_version} (${version_code})"
  s3_put "$apk" "$pinned_key" "application/vnd.android.package-archive"
  s3_put "$apk" "$latest_key" "application/vnd.android.package-archive"

  manifest_tmp="$(mktemp -t langbangtrans-g2.XXXXXX.json)"
  tmp_files+=("$manifest_tmp")
  jq -n \
    --arg instanceId "com.sponic.langbangtrans" \
    --arg displayName "LangBangTrans G2 Translate" \
    --arg direction "g2trans" \
    --arg versionName "$full_version" \
    --arg url "$latest_url" \
    --arg pinnedUrl "$pinned_url" \
    --arg apiBase "$API_BASE" \
    --arg publicR2Base "$PUBLIC_BASE" \
    --arg notes "Native Android G2 translation bridge for Gemini 3.5 Live Translate." \
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
  s3_put "$manifest_tmp" "$manifest_key" "application/json" "no-cache, max-age=0"

  for url in "$pinned_url" "$latest_url" "${PUBLIC_BASE}/${manifest_key}"; do
    local code
    code="$(curl -sI -o /dev/null -w '%{http_code}' "${url}?verify=$(date +%s)")"
    [ "$code" = "200" ] || { echo "verify failed: $url -> HTTP $code" >&2; exit 1; }
  done
}

append_page_row_from_manifest() {
  local anchor="$1"
  local tab="$2"
  local fallback_display="$3"
  local manifest_url="$4"
  local latest_fallback="$5"
  local pinned_fallback="$6"
  local manifest_json display version_code version_name latest pinned
  manifest_json="$(curl -fsS -H "Cache-Control: no-cache" "${manifest_url}?page=$(date +%s)" 2>/dev/null || true)"
  if [ -n "$manifest_json" ] && jq -e . >/dev/null 2>&1 <<< "$manifest_json"; then
    display="$(jq -r --arg fallback "$fallback_display" '.displayName // $fallback' <<< "$manifest_json")"
    version_code="$(jq -r '.versionCode // empty' <<< "$manifest_json")"
    version_name="$(jq -r '.versionName // "pending publish"' <<< "$manifest_json")"
    latest="$(jq -r --arg fallback "$latest_fallback" '.url // $fallback' <<< "$manifest_json")"
    pinned="$(jq -r --arg fallback "$pinned_fallback" '.pinnedUrl // $fallback' <<< "$manifest_json")"
  else
    display="$fallback_display"
    version_code=""
    version_name="pending publish"
    latest="$latest_fallback"
    pinned="$pinned_fallback"
  fi
  PAGE_ROWS+=("${anchor}|${tab}|${display}|${version_code:+v${version_code}}|${version_name}|${latest}|${pinned}|${manifest_url}")
}

if [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "en-pl" ]; then
  publish_channel "enPl" "en-pl" "langbangml-en-pl" "English speakers learning Polish"
fi
if [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "pl-en" ]; then
  publish_channel "plEn" "pl-en" "langbangml-pl-en" "Polish speakers learning English"
fi
if [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "en-ja" ]; then
  publish_channel "enJa" "en-ja" "langbangml-en-ja" "English speakers learning Japanese"
fi
if [ -z "$ONLY_CHANNEL" ] || [ "$ONLY_CHANNEL" = "g2trans" ]; then
  publish_g2trans_channel
fi

PAGE_ROWS=()
append_page_row_from_manifest \
  "en-pl" \
  "English to Polish" \
  "English speakers learning Polish" \
  "${PUBLIC_BASE}/langbang/builds/en-pl/latest.json" \
  "${PUBLIC_BASE}/langbang/builds/en-pl/langbangml-en-pl-latest.apk" \
  "${PUBLIC_BASE}/langbang/builds/en-pl/langbangml-en-pl-latest.apk"
append_page_row_from_manifest \
  "pl-en" \
  "Polish to English" \
  "Polish speakers learning English" \
  "${PUBLIC_BASE}/langbang/builds/pl-en/latest.json" \
  "${PUBLIC_BASE}/langbang/builds/pl-en/langbangml-pl-en-latest.apk" \
  "${PUBLIC_BASE}/langbang/builds/pl-en/langbangml-pl-en-latest.apk"
append_page_row_from_manifest \
  "en-ja" \
  "English to Japanese" \
  "English speakers learning Japanese" \
  "${PUBLIC_BASE}/langbang/builds/en-ja/latest.json" \
  "${PUBLIC_BASE}/langbang/builds/en-ja/langbangml-en-ja-latest.apk" \
  "${PUBLIC_BASE}/langbang/builds/en-ja/langbangml-en-ja-latest.apk"
append_page_row_from_manifest \
  "g2trans" \
  "G2 Translate" \
  "LangBangTrans G2 Translate" \
  "${PUBLIC_BASE}/langbang/builds/g2trans/latest.json" \
  "${PUBLIC_BASE}/langbang/builds/g2trans/langbangtrans-latest.apk" \
  "${PUBLIC_BASE}/langbang/builds/g2trans/langbangtrans-latest.apk"

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
HTML
  echo '  <p>APK channel downloads from the new Cloudflare account. Each published channel has its own latest APK, pinned APK, and update manifest.</p>'
  printf '  <nav class="tabs">'
  for row in "${PAGE_ROWS[@]}"; do
    IFS='|' read -r anchor tab _display _version _full _latest _pinned _manifest <<< "$row"
    printf '<a href="#%s">%s</a>' "$anchor" "$tab"
  done
  printf '</nav>\n'
  for row in "${PAGE_ROWS[@]}"; do
    IFS='|' read -r anchor _tab display version full latest pinned manifest <<< "$row"
    [ -n "$version" ] || version="latest"
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

s3_put "$page_tmp" "langbang/builds/index.html" "text/html" "no-cache, max-age=0"
s3_put "$page_tmp" "langbang/builds/builds.html" "text/html" "no-cache, max-age=0"

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
  IFS='|' read -r _anchor _tab display version full latest pinned manifest <<< "$row"
  [ -n "$version" ] || version="latest"
  echo "  ${display}: ${version} ${latest}"
done
echo "  builds page: ${PUBLIC_BASE}/langbang/builds/index.html"
echo "  live site: ${SITE_BUILDS_URL}"
