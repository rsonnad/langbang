# Prompt 2: Cleanup Old LangBang Cloudflare Account

You are working in the LangBangML fork:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

Goal: after safe arrival on the new Cloudflare account has been verified, delete
all old Cloudflare resources/content that belonged to LangBang/LangBangML from
the old Cloudflare account.

This is destructive. Do not proceed unless the migration evidence file exists
and independently verifies safe arrival, including final client config,
published build manifests, and tablet install evidence:

```text
docs/cloudflare-migration/new-account-arrival-verification.md
```

Do not ask the user to perform cleanup steps you can perform. Do not delete
anything unrelated to LangBang/LangBangML.

Also do not proceed if the evidence file says any final publish/install/client
verification step is still pending.

## Old Account State

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
```

Use old-account credentials only for cleanup. New-account credentials and any
new operational tokens should live in Bitwarden collection `devops-langbang`.

## Live Dependency Gate

The old `alpacapps/langbang/` prefix can contain both LangBangML backend/audio
objects and original LangBang APK/update artifacts. Before deleting that prefix,
prove that every live LangBangML and LangBang dependency has either moved to the
new account or is intentionally retired.

Check at minimum:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
/Users/rahulio/Documents/CodingProjects/langbang
/Users/rahulio/Documents/CodingProjects/genalpaca-admin/rahulio/pages/langbang
```

Look for old-account URLs, update manifests, landing-page links, old R2
Bitwarden item names, and legacy publish scripts. If the original LangBang app,
landing page, build history, update checker, or any still-used install path
depends on the old public R2 base, do not delete all of `langbang/`; either
migrate that dependency first, narrow deletion to proven LangBangML-only keys,
or stop.

## Safety Gate

Before deleting anything, re-verify the new account live state from scratch:

```bash
curl -fsS https://langbangml-api.langbangml.workers.dev/health
curl -fsS https://langbangml-api.langbangml.workers.dev/v1/instances
curl -fsS https://langbangml-api.langbangml.workers.dev/v1/instances/langbangml-en-pl/bootstrap
curl -fsS https://langbangml-api.langbangml.workers.dev/v1/instances/langbangml-pl-en/bootstrap
scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
scripts/langbangml-content-api.sh GET /v1/admin/content/pl-en-v1/lessons
curl -fsS https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json
curl -fsS https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json
```

Confirm:

```text
new D1 row counts match or intentionally exceed old counts, with per-table evidence
old R2 keys under langbang/ all exist in new R2 with matching size/ETag where available
new R2 langbang/ object count and bytes match or intentionally exceed old counts, with any extra keys explained
sample public audio URLs on the new R2 base return 200
new build manifest URLs on the new R2 base return 200 and point only to the new R2 base
new Worker protected content API works for both en-pl-v1 and pl-en-v1
Android/client config and installed tablet cloud-config no longer point at old Worker or old R2 public base
active landing pages and update manifests no longer point at the old R2 base unless explicitly retained
```

If any check fails, stop and do not delete anything.

## Inventory Old Resources

Create an inventory file before deletion:

```text
docs/cloudflare-migration/old-account-cleanup-inventory.md
```

Inventory must include:

```text
old Worker scripts/routes/triggers matching LangBang/LangBangML
old D1 databases matching LangBang/LangBangML
old R2 buckets that contain langbang/
old R2 object count and bytes under langbang/
old R2 key manifest under langbang/ with key, size, and ETag if available
old-vs-new R2 key diff showing no missing old keys in the new bucket
old Worker secrets/config names
old Cloudflare API tokens or Bitwarden items considered obsolete, with shared alpacapps usage checked
legacy scripts/docs that could republish to the old account
```

Important: if old bucket `alpacapps` contains unrelated prefixes, only delete
the `langbang/` prefix. Do not delete the whole bucket unless it contains only
LangBang content.

## Cleanup Actions

After the safety gate and inventory are complete:

1. Quiesce the old Worker first so the old account cannot generate new D1/R2
   writes while cleanup is running. Delete or disable old Worker script(s),
   routes, and triggers for:

```text
langbangml-api
```

Record the exact deletion result and verify the old Worker URL no longer serves
the old `langbangml-api` service.

2. Re-run old D1/R2 inventory after the Worker is quiesced. If old D1 rows or
   old R2 keys changed after the first inventory, update the inventory, re-check
   the new account copy, and stop unless the new account still contains every
   old key and row that should survive.

3. Delete old R2 objects under:

```text
s3://alpacapps/langbang/
```

Delete only that prefix unless the inventory proves the whole bucket is
LangBang-only. If the Live Dependency Gate found active original LangBang
dependencies on this prefix, do not delete the whole prefix; delete only a
documented LangBangML-only subset or stop.

4. Delete old LangBang/LangBangML D1 database(s), including:

```text
langbangml / 0c56de2d-285a-4f72-b629-29b508eb8348
```

5. Remove or mark obsolete old-account Bitwarden items only after confirming
they are not used by the new account, original LangBang, or unrelated alpacapps
services. Prefer adding an `obsolete after LangBangML migration` note if
deletion could break auditability.

6. Search the LangBangML repo for old account references and remove them from
active config, active agent instructions, active docs, and active scripts:

```text
9cd3a280a54ce2a5b382602f0247b577
pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev
0c56de2d-285a-4f72-b629-29b508eb8348
alpacapps.workers.dev
Cloudflare R2 — Object Storage
DevOps-alpacapps
BUCKET="alpacapps"
publish-r2.sh
langbang/langbang-latest.apk
langbang/langbang-latest.json
```

Keep historical references only in migration evidence files where useful.
Retire or hard-fail legacy scripts that would publish LangBangML artifacts back
to the old `alpacapps` bucket.

## Post-Cleanup Verification

Create final cleanup evidence:

```text
docs/cloudflare-migration/old-account-cleanup-complete.md
```

Include:

```text
new-account verification repeated after cleanup
old Worker deletion result
old D1 deletion result
old R2 langbang/ deletion result
old R2 prefix count after deletion, or exact retained keys and why
live dependency gate result for original LangBang and LangBangML
remaining old-account references and why any remain
Bitwarden item changes
exact commands used or script names used
```

Final answer should state:

```text
new account still serving LangBangML
old account LangBang/LangBangML Worker/D1/R2 content deleted
anything intentionally retained
anything blocked
```
