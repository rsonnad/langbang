#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
scripts/publish-r2.sh is disabled in LangBangML.

LangBangML publishes flavor-specific APKs to the new Cloudflare account with:

  scripts/publish-langbangml-builds.sh

This guard prevents republishing LangBangML artifacts to the old alpacapps R2
bucket during or after Cloudflare cleanup.
MSG

exit 1
