# Prompt 1: Migrate LangBangML To New Cloudflare Account

You are working in the LangBangML fork:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

Goal: migrate the entire LangBangML Cloudflare backend to a new Cloudflare
account, using Bitwarden collection `devops-langbang` for the new Cloudflare
tokens and secrets. Complete as much of the migration as possible, verify live
arrival, and leave the old Cloudflare account untouched.

Do not ask the user to do anything you can do. Do not delete old-account
resources in this prompt.

## Current Old Account State

Current old Cloudflare state is documented in:

```text
cloudflare/langbangml/wrangler.toml
cloudflare/langbangml/README.md
```

Known old values:

```text
old account_id: 9cd3a280a54ce2a5b382602f0247b577
old worker: langbangml-api
old worker URL: https://langbangml-api.alpacapps.workers.dev
old D1 database: langbangml
old D1 database_id: 0c56de2d-285a-4f72-b629-29b508eb8348
old R2 bucket: alpacapps
old R2 langbang prefix: langbang/
old public R2 base: https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev
old audio prefix: langbang/audio
```

New Cloudflare credentials and account-specific tokens must live in Bitwarden
collection `devops-langbang`. Use existing items in that collection if present.
If needed, create narrowly scoped items there with clear names, for example:

```text
Cloudflare — LangBangML Worker Deploy — New Account
Cloudflare — LangBangML Content API — New Account
Cloudflare R2 — LangBangML — New Account
```

## Required Migration

1. Identify the new Cloudflare account ID from the `devops-langbang` Bitwarden
   collection. Use the least-privileged working token available.
2. Create new-account Cloudflare resources:
   - D1 database for LangBangML.
   - R2 bucket for LangBangML assets/audio/APKs, preferably dedicated to
     LangBangML rather than sharing `alpacapps`.
   - Worker `langbangml-api` or an unambiguous new equivalent.
3. Copy all D1 data from old to new:
   - `language_pairs`
   - `app_instances`
   - `ui_labels`
   - `content_versions`
   - `content_lessons`
   - `audio_assets`
   - `content_audio_requirements`
   - `sync_events` if useful for audit history
4. Copy all R2 objects under the old LangBang prefix to the new bucket/prefix:
   - at minimum `langbang/audio/`
   - any LangBang APK/build/update objects
   - any sentence/content objects under `langbang/`
   - do not copy unrelated old-account objects outside `langbang/`
5. Update `cloudflare/langbangml/wrangler.toml` for the new account:
   - new `account_id`
   - new D1 `database_id`
   - new R2 `bucket_name`
   - new `PUBLIC_R2_BASE`
   - keep `AUDIO_PREFIX` unless there is a strong reason to change it
6. Set required Worker secrets in the new account:
   - `AZURE_SPEECH_KEY`
   - `CONTENT_API_TOKEN`
   - any other secret discovered in the old Worker
7. Deploy the Worker to the new account.
8. Update Android/client config only if the Worker URL or R2 public base changes:
   - `BuildConfig.LANGBANGML_API_BASE`
   - any docs/scripts that still point to the old Worker URL or old R2 base
9. Build and install if client code changes. Follow the LangBang build/install
   policy for the tablet if applicable.

## Verification Requirements

Produce hard evidence before declaring success:

```bash
curl -fsS <new-worker-url>/health
curl -fsS <new-worker-url>/v1/instances
curl -fsS <new-worker-url>/v1/instances/langbangml-en-pl/bootstrap
curl -fsS <new-worker-url>/v1/instances/langbangml-pl-en/bootstrap
```

Verify the new bootstrap responses match expected live shape:

```text
langbangml-en-pl: pair en-pl, content en-pl-v1, 6 lessons, UX labels present
langbangml-pl-en: pair pl-en, content pl-en-v1, 1+ lessons, UX labels present
```

Verify protected admin API on the new Worker:

```bash
scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
```

Verify audio/R2 arrival:

```text
Compare old vs new R2 object counts under langbang/
Compare old vs new total bytes under langbang/
HEAD/GET sample new public audio URLs from audio_assets
Run POST /v1/admin/content/en-pl-v1/audio/warm-missing with dryRun=true
Run POST /v1/admin/content/pl-en-v1/audio/warm-missing with dryRun=true
```

If any client config changes, verify the installed app syncs from the new
Worker by inspecting the on-device `cloud-config.xml` or through UI if the
tablet display is awake.

## Deliverables

Create a migration evidence file:

```text
docs/cloudflare-migration/new-account-arrival-verification.md
```

That file must include:

```text
new account ID
new Worker name and URL
new D1 database name and ID
new R2 bucket name and public base
old-vs-new D1 row counts per table
old-vs-new R2 object count and bytes under langbang/
bootstrap verification summaries
admin API verification result
audio warm dry-run results
client build/install state if changed
explicit statement that old resources were not deleted
```

Also update:

```text
cloudflare/langbangml/README.md
cloudflare/langbangml/wrangler.toml
scripts/langbangml-content-api.sh
any migration helper scripts you create
```

End with exact state: migrated, partially migrated, or blocked, and name the
specific blocker if blocked.
