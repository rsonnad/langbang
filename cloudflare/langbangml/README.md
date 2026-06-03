# LangBangML Cloudflare Backend

Cloudflare is the only backend for LangBangML.

- Worker: `https://langbangml-api.alpacapps.workers.dev`
- D1 database: `langbangml`
- R2 bucket: `alpacapps`
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
export BW_SESSION=$(~/bin/bw-unlock)
export CLOUDFLARE_API_TOKEN=$(bw get item "Cloudflare — LangBangML Worker Deploy" | jq -r '.login.password')
npx wrangler d1 migrations apply langbangml --remote
```

The seed migrations are idempotent. Reapplying them refreshes the base rows
with `INSERT OR REPLACE`.

## Deploy Worker

```bash
export BW_SESSION=$(~/bin/bw-unlock)
export CLOUDFLARE_API_TOKEN=$(bw get item "Cloudflare — LangBangML Worker Deploy" | jq -r '.login.password')
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
`Cloudflare — LangBangML Content API`.

```bash
scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
```

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
