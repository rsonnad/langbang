#!/usr/bin/env bash
# trigger-pregen-sentences.sh — Tell the langbang-pregen-sentences Edge Function
# to walk every canonical verb/adjective/adverb/noun in the app's bundled assets and
# generate sentence bundles into R2 at langbang/sentences/v{N}/.
#
# Single edge invocations would blow past Supabase's per-function wall clock
# (122 lemmas × ~27s ≈ 9 min). So this script slices the lemma list into
# chunks small enough to finish well under the limit (default 8 lemmas per
# invocation × parallelism 6 ≈ 40s wall time) and fires them concurrently
# from the local shell (default 4 in flight). After every chunk returns the
# script rebuilds the canonical manifest at langbang/sentences/v{N}/manifest.json
# by walking R2 (since each function call only sees its own chunk).
#
# Usage:
#   ./scripts/trigger-pregen-sentences.sh [--refresh] [--chunk N] [--concurrency N]
#                                          [--parallelism N] [--version N]
#
# Defaults: chunk=8 lemmas, concurrency=4 in-flight invocations, parallelism=6
# Gemini calls per invocation. Tune up if the function isn't timing out and
# you want to burn through faster.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

REFRESH=false
PARALLELISM=6
CHUNK=8
CONCURRENCY=4
PROMPT_VERSION=""

# Parse flags. Supports both `--flag value` and `--flag=value` forms.
while [ $# -gt 0 ]; do
  case "$1" in
    --refresh) REFRESH=true ;;
    --parallelism) shift; PARALLELISM="$1" ;;
    --parallelism=*) PARALLELISM="${1#*=}" ;;
    --chunk) shift; CHUNK="$1" ;;
    --chunk=*) CHUNK="${1#*=}" ;;
    --concurrency) shift; CONCURRENCY="$1" ;;
    --concurrency=*) CONCURRENCY="${1#*=}" ;;
    --version) shift; PROMPT_VERSION="$1" ;;
    --version=*) PROMPT_VERSION="${1#*=}" ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

# Pull prompt version from the Kotlin source so the script can't drift out of
# sync with what the client expects.
if [ -z "$PROMPT_VERSION" ]; then
  PROMPT_VERSION=$(grep -oE 'SENTENCE_PROMPT_VERSION = [0-9]+' \
    app/src/main/kotlin/com/sponic/langbang/integrations/GeminiClient.kt | \
    awk '{print $NF}')
fi
[ -n "$PROMPT_VERSION" ] || { echo "could not infer SENTENCE_PROMPT_VERSION" >&2; exit 1; }
echo "→ prompt version v$PROMPT_VERSION  chunk=$CHUNK  concurrency=$CONCURRENCY  parallelism=$PARALLELISM  refresh=$REFRESH"

# Anon key — published in genalpaca-admin/shared/supabase.js; RLS gates real data.
ANON_KEY=$(grep -oE 'SUPABASE_ANON_KEY=[A-Za-z0-9._-]+' local.properties | cut -d= -f2-)
[ -n "$ANON_KEY" ] || { echo "SUPABASE_ANON_KEY missing from local.properties" >&2; exit 1; }
URL="https://aphrrfprbixmhissnjfn.supabase.co/functions/v1/langbang-pregen-sentences"

# Build the master list of (type, payload) entries. Each entry is one lemma.
# We mix verbs/adjectives/adverbs in a single round-robin so a single chunk
# rarely gets unlucky with all-the-slow-ones lining up.
TMP=$(mktemp -d /tmp/langbang-pregen.XXXXXX)
trap 'rm -rf "$TMP"' EXIT
echo "→ tmp dir: $TMP"

jq -c '.verbs[]' app/src/main/assets/lesson-02.json | \
  awk '{print "v\t" $0}' > "$TMP/all.tsv"
jq -c '.adjectives[]' app/src/main/assets/lesson-03.json | \
  awk '{print "a\t" $0}' >> "$TMP/all.tsv"
jq -c '.adverbs[]' app/src/main/assets/lesson-04.json | \
  awk '{print "d\t" $0}' >> "$TMP/all.tsv"
jq -c '.nouns[]' app/src/main/assets/lesson-06.json | \
  awk '{print "n\t" $0}' >> "$TMP/all.tsv"

TOTAL=$(wc -l < "$TMP/all.tsv" | tr -d ' ')
echo "→ total lemmas: $TOTAL"

# Slice into chunks of $CHUNK lines each, then convert each chunk into a
# {verbs, adjectives, adverbs, nouns} payload the Edge Function expects.
split -l "$CHUNK" "$TMP/all.tsv" "$TMP/chunk-"

CHUNK_COUNT=0
for chunk in "$TMP"/chunk-*; do
  CHUNK_COUNT=$((CHUNK_COUNT + 1))
  VERBS=$(awk -F'\t' '$1=="v"{print $2}' "$chunk" | jq -s '.')
  ADJ=$(awk -F'\t' '$1=="a"{print $2}' "$chunk" | jq -s '.')
  ADV=$(awk -F'\t' '$1=="d"{print $2}' "$chunk" | jq -s '.')
  NOUNS=$(awk -F'\t' '$1=="n"{print $2}' "$chunk" | jq -s '.')
  jq -n \
    --argjson v "$VERBS" --argjson a "$ADJ" --argjson d "$ADV" --argjson n "$NOUNS" \
    --argjson version "$PROMPT_VERSION" \
    --argjson para "$PARALLELISM" \
    --argjson refresh "$REFRESH" \
    '{promptVersion: $version, refresh: $refresh, parallelism: $para,
      verbs: $v, adjectives: $a, adverbs: $d, nouns: $n}' > "$chunk.body.json"
done
echo "→ split into $CHUNK_COUNT chunks"

# Fire chunks with bounded concurrency. Each call captures its summary so we
# can roll up at the end without re-parsing every response payload.
mkdir -p "$TMP/results"

post_chunk() {
  local body_file="$1"
  local idx="$2"
  local total="$3"
  local out="$TMP/results/$(basename "$body_file" .body.json).json"
  local start end
  start=$(date +%s)
  if curl -sS -X POST \
       -H "Content-Type: application/json" \
       -H "Authorization: Bearer $ANON_KEY" \
       -H "apikey: $ANON_KEY" \
       --max-time 120 \
       -d @"$body_file" \
       "$URL" > "$out" 2>&1; then
    end=$(date +%s)
    local up cached fail
    up=$(jq -r '.summary.uploaded // 0' "$out" 2>/dev/null || echo 0)
    cached=$(jq -r '.summary.cached // 0' "$out" 2>/dev/null || echo 0)
    fail=$(jq -r '.summary.failed // 0' "$out" 2>/dev/null || echo 0)
    printf "  ✓ chunk %d/%d  uploaded=%s cached=%s failed=%s  (%ss)\n" \
      "$idx" "$total" "$up" "$cached" "$fail" "$((end - start))"
  else
    printf "  ✗ chunk %d/%d  curl/http error — see %s\n" \
      "$idx" "$total" "$out" >&2
  fi
}

export -f post_chunk
export ANON_KEY URL TMP

# xargs -P drives concurrency. Each chunk index is fed to a subshell that knows
# how to find its own body file. -n1 so each subshell handles exactly one chunk.
i=0
for chunk in "$TMP"/chunk-*.body.json; do
  i=$((i + 1))
  echo "$i $chunk"
done | xargs -P "$CONCURRENCY" -n2 -I{} bash -c '
  args=({});
  idx="${args[0]}";
  body="${args[1]}";
  post_chunk "$body" "$idx" "'$CHUNK_COUNT'"
'

# Now roll up the canonical manifest by listing R2 directly. Each chunk's
# function call already uploaded its own manifest snippet under the same
# prefix, so we just list every object under langbang/sentences/v{N}/ and
# rebuild the index. This means partial failures still leave a valid
# manifest pointing at whatever DID land.
echo
echo "→ rebuilding manifest from R2 listing"
export BW_SESSION="${BW_SESSION:-$(~/bin/bw-unlock)}"
ITEM=$(bw get item "Cloudflare R2 — Object Storage" --session "$BW_SESSION")
field() { jq -r --arg n "$1" '.fields[] | select(.name==$n) | .value' <<< "$ITEM"; }
export AWS_ACCESS_KEY_ID="$(field 'Access Key ID')"
export AWS_SECRET_ACCESS_KEY="$(field 'Secret Access Key')"
export AWS_DEFAULT_REGION=auto
ENDPOINT="https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com"
PUBLIC_BASE="https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev"
PREFIX="langbang/sentences/v${PROMPT_VERSION}/"

LISTING=$(aws s3 ls "s3://alpacapps/${PREFIX}" --recursive --endpoint-url "$ENDPOINT" | \
  awk '{print $NF}' | grep -v '/manifest\.json$' || true)

# Compose a manifest entry per object. Each entry needs sha256 + count + bytes.
# We pull each bundle (small) to compute sha256 — total ~200KB so a few seconds.
MANIFEST=$(jq -n --argjson v "$PROMPT_VERSION" --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{promptVersion: $v, generatedAt: $ts, entries: {}}')

while IFS= read -r KEY; do
  [ -z "$KEY" ] && continue
  REL="${KEY#$PREFIX}"
  URL_F="${PUBLIC_BASE}/${KEY}"
  BODY=$(curl -sS "$URL_F")
  SHA=$(printf '%s' "$BODY" | shasum -a 256 | awk '{print $1}')
  BYTES=$(printf '%s' "$BODY" | wc -c | tr -d ' ')
  COUNT=$(jq 'length' <<< "$BODY")
  MANIFEST=$(jq --arg key "$REL" --arg sha "$SHA" --argjson bytes "$BYTES" \
    --argjson count "$COUNT" --arg url "$URL_F" \
    '.entries[$key] = {sha256: $sha, count: $count, bytes: $bytes, url: $url}' \
    <<< "$MANIFEST")
done <<< "$LISTING"

MANIFEST_KEY="langbang/sentences/v${PROMPT_VERSION}/manifest.json"
echo "$MANIFEST" > "$TMP/manifest.json"
aws s3 cp "$TMP/manifest.json" "s3://alpacapps/${MANIFEST_KEY}" \
  --endpoint-url "$ENDPOINT" \
  --content-type application/json \
  --no-progress

ENTRY_COUNT=$(jq '.entries | length' <<< "$MANIFEST")
echo
echo "✓ manifest uploaded with $ENTRY_COUNT entries"
echo "  ${PUBLIC_BASE}/${MANIFEST_KEY}"
