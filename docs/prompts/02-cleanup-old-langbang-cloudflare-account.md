# Prompt 2: Cleanup Old LangBang Cloudflare Account

You are working in the LangBangML fork:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

Goal: after safe arrival on the new Cloudflare account has been verified, delete
all old Cloudflare resources/content that belonged to LangBang/LangBangML from
the old Cloudflare account.

This is destructive. Do not proceed unless the migration evidence file exists
and independently verifies safe arrival:

```text
docs/cloudflare-migration/new-account-arrival-verification.md
```

Do not ask the user to perform cleanup steps you can perform. Do not delete
anything unrelated to LangBang/LangBangML.

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

## Safety Gate

Before deleting anything, re-verify the new account live state from scratch:

```bash
curl -fsS <new-worker-url>/health
curl -fsS <new-worker-url>/v1/instances
curl -fsS <new-worker-url>/v1/instances/langbangml-en-pl/bootstrap
curl -fsS <new-worker-url>/v1/instances/langbangml-pl-en/bootstrap
scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons
```

Confirm:

```text
new D1 row counts match or intentionally exceed old counts
new R2 langbang/ object count and bytes match or intentionally exceed old counts
sample public audio URLs on the new R2 base return 200
new Worker protected content API works
Android/client config no longer points at old Worker or old R2 public base
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
old Worker secrets/config names
old Cloudflare API tokens or Bitwarden items considered obsolete
```

Important: if old bucket `alpacapps` contains unrelated prefixes, only delete
the `langbang/` prefix. Do not delete the whole bucket unless it contains only
LangBang content.

## Cleanup Actions

After the safety gate and inventory are complete:

1. Delete old R2 objects under:

```text
s3://alpacapps/langbang/
```

Delete only that prefix unless the inventory proves the whole bucket is
LangBang-only.

2. Delete old LangBang/LangBangML D1 database(s), including:

```text
langbangml / 0c56de2d-285a-4f72-b629-29b508eb8348
```

3. Delete old Worker script(s), routes, and triggers for:

```text
langbangml-api
```

4. Remove or mark obsolete old-account Bitwarden items only after confirming
they are not used by the new account. Prefer adding an “obsolete after
LangBangML migration” note if deletion could break auditability.

5. Search the LangBangML repo for old account references and remove them from
active config:

```text
9cd3a280a54ce2a5b382602f0247b577
pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev
0c56de2d-285a-4f72-b629-29b508eb8348
alpacapps.workers.dev
```

Keep historical references only in migration evidence files where useful.

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
