#!/usr/bin/env bash
set -euo pipefail

API_BASE="${LANGBANGML_API_BASE:-https://langbangml-api.alpacapps.workers.dev}"
METHOD="${1:-}"
PATH_PART="${2:-}"
BODY_FILE="${3:-}"

if [ -z "$METHOD" ] || [ -z "$PATH_PART" ]; then
  cat >&2 <<'USAGE'
Usage:
  scripts/langbangml-content-api.sh METHOD /path [json-file|-]

Examples:
  scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
  scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/lessons/lesson-05/items body.json
  echo '{"dryRun":true,"limit":10}' | scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/audio/warm-missing -

Auth:
  Set LANGBANGML_CONTENT_TOKEN, or unlock Bitwarden so this script can read
  "Cloudflare — LangBangML Content API".
USAGE
  exit 2
fi

TOKEN="${LANGBANGML_CONTENT_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  if [ -z "${BW_SESSION:-}" ]; then
    BW_SESSION="$(~/bin/bw-unlock </dev/null)" || {
      echo "Bitwarden is locked. Run: export BW_SESSION=\$(~/bin/bw-unlock)" >&2
      echo "Or set LANGBANGML_CONTENT_TOKEN directly." >&2
      exit 1
    }
    export BW_SESSION
  fi
  TOKEN="$(bw get item "Cloudflare — LangBangML Content API" --session "$BW_SESSION" </dev/null | jq -r '.login.password')" || {
    echo "Could not read Cloudflare — LangBangML Content API from Bitwarden." >&2
    exit 1
  }
fi

URL="${API_BASE%/}/${PATH_PART#/}"
args=(-fsS -X "$METHOD" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

if [ -n "$BODY_FILE" ]; then
  if [ "$BODY_FILE" = "-" ]; then
    args+=(-d @-)
  else
    args+=(-d @"$BODY_FILE")
  fi
fi

curl "${args[@]}" "$URL"
echo
