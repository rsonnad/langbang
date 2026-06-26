#!/usr/bin/env bash
set -euo pipefail

API_BASE="${LANGBANGML_API_BASE:-https://langbangml-api.langbangml.workers.dev}"
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
	  echo '{"instanceId":"langbangml-en-pl","type":"content_refresh","reason":"manual.content.refresh"}' | scripts/langbangml-content-api.sh POST /v1/admin/push/refresh -

Auth:
  Set LANGBANGML_CONTENT_TOKEN, or unlock Bitwarden so this script can read
  "Cloudflare — LangBangML Content API — New Account", or fall back to
  "Cloudflare — LangBangML Content API".
USAGE
  exit 2
fi

TOKEN="${LANGBANGML_CONTENT_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  BW_CLI="${BW_CLI:-}"
  if [ -z "$BW_CLI" ]; then
    if [ -x /opt/homebrew/bin/bw ]; then
      BW_CLI="/opt/homebrew/bin/bw"
    else
      BW_CLI="bw"
    fi
  fi

  mint_bw_session() {
    BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null || true)"
    if [ -z "$BW_PASSWORD" ]; then
      echo "Could not read Bitwarden CLI password from Keychain." >&2
      echo "Or set LANGBANGML_CONTENT_TOKEN directly." >&2
      exit 1
    fi
    export BW_PASSWORD
    BW_SESSION="$("$BW_CLI" unlock --passwordenv BW_PASSWORD --raw </dev/null 2>/dev/null || true)"
    unset BW_PASSWORD
    if [ -z "$BW_SESSION" ]; then
      echo "Could not unlock Bitwarden non-interactively." >&2
      echo "Or set LANGBANGML_CONTENT_TOKEN directly." >&2
      exit 1
    fi
    export BW_SESSION
  }

  if [ -n "${BW_SESSION:-}" ]; then
    if ! "$BW_CLI" status --session "$BW_SESSION" </dev/null 2>/dev/null | jq -e '.status == "unlocked"' >/dev/null 2>&1; then
      unset BW_SESSION
    fi
  fi
  if [ -z "${BW_SESSION:-}" ]; then
    mint_bw_session
  fi

  for item_name in \
    "Cloudflare — LangBangML Content API — New Account" \
    "Cloudflare — LangBangML Content API"
  do
    TOKEN="$(
      "$BW_CLI" list items --search "$item_name" --session "$BW_SESSION" </dev/null 2>/dev/null \
        | jq -r --arg item_name "$item_name" '
          [.[] | select(.name == $item_name)][0]
          | .login.password // empty
        '
    )"
    if [ -n "$TOKEN" ]; then
      break
    fi
  done

  if [ -z "$TOKEN" ]; then
    echo "Could not read LangBangML content API token from Bitwarden." >&2
    echo "Or set LANGBANGML_CONTENT_TOKEN directly." >&2
    exit 1
  fi
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
