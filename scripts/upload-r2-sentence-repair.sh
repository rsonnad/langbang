#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

PATCH_ROOT="${1:-}"
if [ -z "$PATCH_ROOT" ]; then
  echo "usage: scripts/upload-r2-sentence-repair.sh /path/to/repair-output" >&2
  exit 2
fi

SENTENCE_DIR="$PATCH_ROOT/langbang/sentences/v4"
MANIFEST="$SENTENCE_DIR/manifest.json"
if [ ! -f "$MANIFEST" ]; then
  echo "missing repair manifest: $MANIFEST" >&2
  exit 2
fi

BUCKET="langbangml"
R2_ITEM="Cloudflare R2 - LangBang S3 Admin"
PUBLIC_BASE="https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

field() {
  jq -r --arg n "$1" '.fields[]? | select(.name==$n) | .value' <<< "$BW_ITEM"
}

need aws
need bw
need curl
need jq

if [ -z "${BW_SESSION:-}" ] || ! bw status --session "$BW_SESSION" </dev/null 2>/dev/null | jq -e '.status == "unlocked"' >/dev/null; then
  BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null || true)"
  [ -n "$BW_PASSWORD" ] || { echo "could not read Bitwarden CLI password from Keychain" >&2; exit 1; }
  export BW_PASSWORD
  BW_SESSION="$(/opt/homebrew/bin/bw unlock --passwordenv BW_PASSWORD --raw </dev/null)"
  unset BW_PASSWORD
  export BW_SESSION
fi

BW_ITEM="$(bw get item "$R2_ITEM" --session "$BW_SESSION" </dev/null)"

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

aws s3 cp "$SENTENCE_DIR" "s3://${BUCKET}/langbang/sentences/v4" \
  --recursive \
  --endpoint-url "$ENDPOINT" \
  --exclude "*" \
  --include "*.json" \
  --content-type application/json \
  --cache-control "no-cache, max-age=0" \
  --no-progress

manifest_code="$(curl -sI -o /dev/null -w '%{http_code}' "${PUBLIC_BASE}/langbang/sentences/v4/manifest.json?verify=$(date +%s)")"
[ "$manifest_code" = "200" ] || { echo "manifest verify failed: HTTP $manifest_code" >&2; exit 1; }

duzy_code="$(curl -sI -o /dev/null -w '%{http_code}' "${PUBLIC_BASE}/langbang/sentences/v4/adjectives/du%C5%BCy.json?verify=$(date +%s)")"
[ "$duzy_code" = "200" ] || { echo "duży bundle verify failed: HTTP $duzy_code" >&2; exit 1; }

echo "uploaded sentence repair to s3://${BUCKET}/langbang/sentences/v4"
