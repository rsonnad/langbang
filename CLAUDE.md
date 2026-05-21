# LangBang — Project Directives

Personal-use Android tablet app for learning Polish. Kotlin + Jetpack Compose,
min SDK 33, target SDK 36, arm64-v8a only. Source code lives at
[github.com/rsonnad/langbang](https://github.com/rsonnad/langbang).

**Releases live on Cloudflare R2, not on GitHub Releases.** Every build is
published to the `alpacapps` R2 bucket under `langbang/`:

- Pinned: `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/langbang-v{N}-arm64.apk`
- Latest: `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/langbang-latest.apk`

To publish a new build, run **`./scripts/publish-r2.sh`** — it builds, uploads
both keys, verifies the public URLs return 200, and patches the version label
on the landing page. Do **not** create GitHub Releases for this project.

## Shared infrastructure (alpacapps)

LangBang reuses three pieces of the alpacapps platform. Full credentials and
runbooks live in the alpacapps repo at
`/Users/rahulio/Documents/CodingProjects/genalpaca-admin/docs/CREDENTIALS.md`
([github.com/rsonnad/alpacapps](https://github.com/rsonnad/alpacapps)) — this
file just records that langbang is a tenant.

> **Always use the existing recipes, don't invent new ones.**
> When accessing any alpacapps-shared service (Supabase, R2, Cloudflare,
> SignWell, Telnyx, Oracle, etc.), copy the canonical recipe from
> `genalpaca-admin/docs/CREDENTIALS.md` verbatim — the inline
> `$(bw-read ...)` form is load-bearing (see Recipes section below for the
> langbang-specific subset). Local gotchas and any working recipes
> discovered during a session belong in [`memory/service-access.md`](memory/service-access.md).
> If a recipe fails, fix the BW item or the recipe at its source — don't
> work around it inline.

### 1. Supabase — `aphrrfprbixmhissnjfn` project, `langbang` schema

- **URL:** `https://aphrrfprbixmhissnjfn.supabase.co`
- **Schema:** `langbang` (isolated from alpacapps `public`). Created by
  `genalpaca-admin/supabase/migrations/20260520_langbang_schema.sql`.
- **Client wiring:** `app/src/main/kotlin/com/sponic/langbang/data/SupabaseClient.kt`
  installs `Postgrest { defaultSchema = "langbang" }`.
- **Keys:** `SUPABASE_URL` + `SUPABASE_ANON_KEY` flow via `local.properties` →
  `BuildConfig`. Anon key is already public in
  `genalpaca-admin/shared/supabase.js` — RLS does the gating.
- **Admin access:** `bw-read "Supabase — AlpacApps Project" "Management API Token"`
  (in DevOps-alpacapps vault). For SQL: POST to
  `https://api.supabase.com/v1/projects/aphrrfprbixmhissnjfn/database/query`.
- **Required setup step:** in Supabase Studio → Project Settings → API →
  Exposed schemas, add `langbang` alongside `public, graphql_public`.
  PostgREST won't route requests otherwise.

### 2. Cloudflare R2 — `alpacapps` bucket

- **Bucket:** `alpacapps` (shared with alpacapps proper)
- **S3 endpoint:** `https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com`
- **Public read URL:** `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/<key>`
- **Path convention:** put langbang objects under `langbang/` (e.g.
  `langbang/audio/<sha1>.mp3`, `langbang/builds/app-<ver>.apk`) so they don't
  collide with alpacapps' `housephotos/`, `lease-documents/`, etc.
- **Credentials:** `bw-read "Cloudflare R2 — Object Storage" "Access Key ID"`
  / `"Secret Access Key"` (DevOps-shared vault). The Cloudflare DNS token
  does NOT have R2 write perms — always use the S3 keys.
- **Free tier headroom:** 10 GB storage, 10M reads/mo, 1M writes/mo, zero
  egress. App backups currently SFTP to ALPUCA — R2 is available as an
  alternative or mirror.

### 3. GitHub Pages — alpacaplayhouse.com

- **Live page:** `https://alpacaplayhouse.com/rahulio/pages/langbang/` (after
  the alpacapps repo is pushed) — source at
  `genalpaca-admin/rahulio/pages/langbang/index.html`. Links to GitHub
  Releases for APK downloads.
- **Manifest entry:** `genalpaca-admin/rahulio/pages/pages-manifest.json`
  (section: "Projects").
- **Deploy mechanism:** Cloudflare Pages auto-deploys on push to `main` of
  the alpacapps repo. No build step.
- **Custom domain CNAME:** `alpacaplayhouse.com` (set in
  `genalpaca-admin/CNAME`).

## Build & version

- **Version source:** `version.properties` at repo root. `app/build.gradle.kts`
  bumps `buildNumber` on every assemble/bundle/install; `versionName` is
  manually bumped on releases.
- **Current displayed version:** `<versionName>.<buildNumber>` (e.g.
  `0.1.6.20`). Surfaces in Settings → version header.
- **APK output:** `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (~60 MB,
  Speech SDK native libs dominate).
- **Install over Tailscale:** `~/bin/adb-tab` connects to the Tab A9+; then
  `adb -s <ip:port> install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

## Secrets summary (`local.properties`)

```
AZURE_SPEECH_KEY=<bw item 3d26dda5-fea1-4362-b30c-b44f001e860c>
AZURE_SPEECH_REGION=eastus
GEMINI_API_KEY=<bw item c4b16931-335b-457a-9910-b416006d3b8c>
SUPABASE_URL=https://aphrrfprbixmhissnjfn.supabase.co
SUPABASE_ANON_KEY=<public, mirrors genalpaca-admin/shared/supabase.js>
```

All five flow through `buildConfigField` declarations in
`app/build.gradle.kts` and are read as `BuildConfig.<NAME>` at runtime.

## Recipes — easy access

All BW commands assume `export BW_SESSION=$(~/bin/bw-unlock)` has been run
this shell. `bw-read` resolves the named item; recipes below are
copy-paste verbatim.

### Supabase — run SQL against the langbang schema

```bash
TOKEN=$(bw-read "Supabase — AlpacApps Project" "Management API Token")
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"SELECT * FROM langbang.<table> LIMIT 5;"}' \
  "https://api.supabase.com/v1/projects/aphrrfprbixmhissnjfn/database/query"
```

### Supabase — add a langbang migration

Migrations live in the alpacapps repo, not here. From any directory:

```bash
F=/Users/rahulio/Documents/CodingProjects/genalpaca-admin/supabase/migrations/$(date +%Y%m%d)_langbang_<name>.sql
cat > "$F" <<'SQL'
-- langbang: <one-line purpose>
CREATE TABLE IF NOT EXISTS langbang.<table> (...);
SQL
```

Apply it the same way as any other SQL (recipe above), or via the
Supabase CLI from the alpacapps repo: `cd genalpaca-admin && supabase db push`.

### Supabase — call from Kotlin

```kotlin
import com.sponic.langbang.data.SupabaseClientHolder
import io.github.jan.supabase.postgrest.postgrest

suspend fun fetchPhrases(): List<Phrase> =
    SupabaseClientHolder.client.postgrest
        .from("phrases")          // resolves to langbang.phrases
        .select()
        .decodeList<Phrase>()
```

`@Serializable data class Phrase(val id: Long, val pl: String, val en: String)`
— schema is implicit from `defaultSchema = "langbang"`.

### R2 — upload a file (S3 API)

```bash
AWS_ACCESS_KEY_ID="$(bw-read "Cloudflare R2 — Object Storage" "Access Key ID")" \
AWS_SECRET_ACCESS_KEY="$(bw-read "Cloudflare R2 — Object Storage" "Secret Access Key")" \
aws s3 cp ./local-file.mp3 s3://alpacapps/langbang/audio/local-file.mp3 \
  --endpoint-url https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com
```

Public URL after upload:
`https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/audio/local-file.mp3`

### R2 — list and delete

```bash
# Same env-var prefix as the upload recipe above.
EP="--endpoint-url https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com"
aws s3 ls s3://alpacapps/langbang/ $EP                 # list
aws s3 rm s3://alpacapps/langbang/audio/foo.mp3 $EP   # delete one
```

### R2 — fetch from the Android app

Public objects need no auth — plain HTTP GET works:

```kotlin
val url = "https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/audio/$sha1.mp3"
URL(url).openStream().use { it.copyTo(FileOutputStream(localFile)) }
```

For writes from the app, prefer a Supabase Edge Function that wraps R2
(see `genalpaca-admin/supabase/functions/_shared/r2-upload.ts`) — keeps
the S3 secret off the device.

### GitHub Pages — update the langbang landing page

```bash
cd /Users/rahulio/Documents/CodingProjects/genalpaca-admin
$EDITOR rahulio/pages/langbang/index.html
# Bump the modifiedAt for this entry in rahulio/pages/pages-manifest.json
git add rahulio/pages/langbang/ rahulio/pages/pages-manifest.json
git commit -m "langbang page: <what changed>"
git push                                  # Cloudflare Pages auto-deploys
```

Live URL: `https://alpacaplayhouse.com/rahulio/pages/langbang/` (typically
~30s after push).

### GitHub Pages — host an asset (screenshot, etc.)

```bash
cp ~/some-screenshot.png \
   /Users/rahulio/Documents/CodingProjects/genalpaca-admin/rahulio/pages/langbang/screenshot.png
# Reference in index.html as <img src="screenshot.png">
```

For large binaries (APKs, audio packs), use R2 or GitHub Releases —
don't commit them to the alpacapps repo.

### Releases — publish a new APK

```bash
cd /Users/rahulio/Documents/CodingProjects/langbang
./gradlew :app:assembleDebug
APK=app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
VER=$(awk -F= '/versionName/{n=$2}/buildNumber/{b=$2}END{print n"."b}' version.properties)
gh release create "v$VER" "$APK" --title "v$VER" --notes "See CHANGELOG"
```

The landing page's "Latest release" link auto-points at
`github.com/rsonnad/langbang/releases/latest`.

## Project Identity Check

This is **langbang**. If the user mentions **alpacapps**, **finleg**,
**portsie**, or **sponic** and the request doesn't match this project,
warn before acting:
> "You mentioned **{keyword}** but this session is in **langbang**. Did
> you mean to run this in the other project?"
