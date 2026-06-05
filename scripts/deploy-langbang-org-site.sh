#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SITE_DIR="$ROOT/cloudflare/langbang-org"
TEMPLATE="$SITE_DIR/worker.template.js"
WORKER_NAME="langbang-placeholder"
BW_ITEM_ID="5080315a-e7f0-4acf-ba36-b45e00424d10"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

data_url() {
  local type="$1"
  local file="$2"
  printf 'data:%s;base64,%s' "$type" "$(base64 < "$file" | tr -d '\n')"
}

need bw
need jq
need curl
need perl
need base64

if [ -z "${BW_SESSION:-}" ] || ! bw status --session "$BW_SESSION" 2>/dev/null | jq -e '.status == "unlocked"' >/dev/null; then
  BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null || true)"
  [ -n "$BW_PASSWORD" ] || { echo "could not read Bitwarden CLI password from Keychain" >&2; exit 1; }
  export BW_PASSWORD
  BW_SESSION="$(/opt/homebrew/bin/bw unlock --passwordenv BW_PASSWORD --raw </dev/null)"
  unset BW_PASSWORD
  export BW_SESSION
fi

BW_ITEM="$(bw get item "$BW_ITEM_ID" --session "$BW_SESSION" </dev/null)"
TOKEN="$(jq -r '.fields[] | select(.name=="Cloudflare API Token") | .value' <<<"$BW_ITEM")"
ACCOUNT_ID="$(jq -r '.fields[] | select(.name=="Account ID") | .value' <<<"$BW_ITEM")"

[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Cloudflare token not found in Bitwarden item $BW_ITEM_ID" >&2; exit 1; }
[ -n "$ACCOUNT_ID" ] && [ "$ACCOUNT_ID" != "null" ] || { echo "Cloudflare account ID not found in Bitwarden item $BW_ITEM_ID" >&2; exit 1; }

LOGO_SQUARE="$(data_url "image/png" "$SITE_DIR/assets/langbang-logo-square.png")"
LOGO_WORDMARK="$(data_url "image/png" "$SITE_DIR/assets/langbang-logo-wordmark.png")"
INSTALL_QR="$(data_url "image/svg+xml" "$SITE_DIR/assets/install-qr.svg")"

TMP_WORKER="$(mktemp)"
trap 'rm -f "$TMP_WORKER"' EXIT

LOGO_SQUARE="$LOGO_SQUARE" \
LOGO_WORDMARK="$LOGO_WORDMARK" \
INSTALL_QR="$INSTALL_QR" \
perl -0pe '
  s#__LOGO_SQUARE_DATA_URL__#$ENV{LOGO_SQUARE}#g;
  s#__LOGO_WORDMARK_DATA_URL__#$ENV{LOGO_WORDMARK}#g;
  s#__INSTALL_QR_DATA_URL__#$ENV{INSTALL_QR}#g;
' "$TEMPLATE" > "$TMP_WORKER"

RESPONSE="$(curl -sS -X PUT \
  "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT_ID/workers/scripts/$WORKER_NAME" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/javascript" \
  --data-binary "@$TMP_WORKER")"

if ! jq -e '.success == true' <<<"$RESPONSE" >/dev/null; then
  jq -r '.errors // .messages // .' <<<"$RESPONSE" >&2
  exit 1
fi

echo "deployed $WORKER_NAME"
