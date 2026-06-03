#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
trigger-pregen-sentences.sh is disabled for LangBangML.

The old script invoked a legacy edge function. LangBangML is Cloudflare-only, so
sentence generation must be moved behind the Cloudflare Worker before this batch
workflow is re-enabled.
MSG

exit 1
