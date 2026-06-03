# LangBangML Cloudflare Backend

Cloudflare is the only backend for LangBangML.

- Worker: `https://langbangml-api.langbangml.workers.dev`
- D1 database: `langbangml`
- R2 bucket: `langbangml`
- Public R2 base: `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`
- Audio prefix: `langbang/audio`

## Endpoints

- `GET /health`
- `GET /v1/instances`
- `GET /v1/instances/:id/bootstrap`
- `GET /v1/labels/:locale`
- `POST /v1/audio/manifest`
- `GET /v1/admin/content/:versionId/lessons`
- `GET /v1/admin/content/:versionId/lessons/:lessonId`
- `GET /v1/admin/content/:versionId/lessons/:lessonId/items?collection=...`
- `POST /v1/admin/content/:versionId/lessons/:lessonId/items`
- `POST /v1/admin/content/:versionId/lessons/:lessonId/reorder`
- `DELETE /v1/admin/content/:versionId/lessons/:lessonId/items`
- `POST /v1/admin/content/:versionId/audio/warm-missing`

The Android app downloads its selected instance bootstrap at launch. The
bootstrap contains the language pair, content version, lesson payloads, UX
labels, instance settings, and audio manifest endpoint.

## Initial Instances

- `langbangml-en-pl`: English UI, English source, Polish target.
- `langbangml-pl-en`: Polish UI, Polish source, English target.

Changing an instance's `content_version_id`, `ui_locale`, `settings_json`, or
language-pair row in D1 changes app behavior on the next sync. No APK update is
needed for content or UX-label changes that are represented in the bootstrap.

## Migrations

Run from this directory:

```bash
BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null)"
export BW_PASSWORD
export BW_SESSION="$(bw unlock --passwordenv BW_PASSWORD --raw)"
export CLOUDFLARE_API_TOKEN="$(bw get item "Cloudflare - LangBang Codex Claude Admin" --session "$BW_SESSION" | jq -r '.fields[] | select(.name == "Cloudflare Account API Token") | .value')"
npx wrangler d1 migrations apply langbangml --remote
```

The seed migrations are idempotent. Reapplying them refreshes the base rows
with `INSERT OR REPLACE`.

## Deploy Worker

```bash
BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w 2>/dev/null)"
export BW_PASSWORD
export BW_SESSION="$(bw unlock --passwordenv BW_PASSWORD --raw)"
export CLOUDFLARE_API_TOKEN="$(bw get item "Cloudflare - LangBang Codex Claude Admin" --session "$BW_SESSION" | jq -r '.fields[] | select(.name == "Cloudflare Account API Token") | .value')"
npx wrangler deploy
```

Set the Azure TTS secret once per Worker environment:

```bash
npx wrangler secret put AZURE_SPEECH_KEY
```

## Warm Audio

For the initial Polish-speaker English-study instance:

```bash
python3 ../../scripts/populate-langbangml-pl-en-audio.py
```

This posts phrases to `/v1/audio/manifest`. The Worker checks R2 first,
synthesizes only missing files, writes audio metadata into D1, and stores mp3s
under `langbang/audio/<sha1>.mp3`.

## Content API

Admin content routes require:

```bash
Authorization: Bearer $LANGBANGML_CONTENT_TOKEN
```

For local coding sessions, use the helper script. It reads the token from
`LANGBANGML_CONTENT_TOKEN` or the Bitwarden item
`Cloudflare — LangBangML Content API — New Account`, falling back to the old
`Cloudflare — LangBangML Content API` item if needed.

```bash
scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
```

## Publish APK Builds

The app now has two debug APK flavors:

- `enPl`: English speakers learning Polish, package `com.sponic.langbangml.enpl`,
  instance `langbangml-en-pl`.
- `plEn`: Polish speakers learning English, package `com.sponic.langbangml.plen`,
  instance `langbangml-pl-en`.

Publish both channels to the new-account R2 bucket:

```bash
scripts/publish-langbangml-builds.sh
```

The script builds both flavors, uploads pinned and latest APKs, writes separate
update manifests, uploads an R2-hosted fallback builds page, deploys the
`langbang.org` Worker page when the site deploy script is available, and verifies
that the live builds page references the just-published latest and pinned URLs:

- `langbang/builds/en-pl/langbangml-en-pl-latest.apk`
- `langbang/builds/en-pl/latest.json`
- `langbang/builds/pl-en/langbangml-pl-en-latest.apk`
- `langbang/builds/pl-en/latest.json`
- `langbang/builds/index.html`

`https://langbang.org/builds` currently serves the public builds page through
the existing `langbang-placeholder` site Worker route while the downloadable
APKs and manifests come from this new Cloudflare account's R2 bucket.

Use `--no-site-deploy` only when you deliberately want to publish R2 artifacts
without touching the `langbang.org` Worker page.

Add a noun:

```json
{
  "collection": "nouns",
  "item": {
    "lemma": "ogród",
    "en": "garden",
    "gender": "m",
    "nom": { "sg": "ogród", "pl": "ogrody" },
    "acc": { "sg": "ogród", "pl": "ogrody" },
    "gen": { "sg": "ogrodu", "pl": "ogrodów" }
  }
}
```

```bash
scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/lessons/lesson-06/items noun.json
```

Add a phrase sentence to a group:

```json
{
  "collection": "sentences",
  "groupId": "intro-rahul",
  "item": {
    "pl": "Lubię uczyć się polskiego.",
    "en": "I like learning Polish.",
    "literal": "I-like to-learn self Polish."
  }
}
```

Reorder groups by stable keys:

```json
{
  "collection": "groups",
  "order": ["welcome-to-poland", "intro-rahul"],
  "dryRun": true
}
```

```bash
scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/lessons/lesson-05/reorder reorder.json
```

Warm missing audio in bounded batches:

```json
{
  "lessonId": "lesson-05",
  "limit": 40,
  "dryRun": false
}
```

```bash
scripts/langbangml-content-api.sh POST /v1/admin/content/en-pl-v1/audio/warm-missing warm.json
```

Use `dryRun: true` on content mutation or audio warm requests to verify the
request shape without writing D1 or R2.
