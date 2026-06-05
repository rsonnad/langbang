# Old Cloudflare Account Cleanup Complete

Date: 2026-06-03

This file records destructive cleanup of old Wingsiebird / alpacapps Cloudflare
resources that belonged to LangBang and LangBangML.

## Summary

- New account still serves LangBangML.
- Old `langbangml-api` Worker was deleted.
- Old `langbangml` D1 database was deleted.
- Old `s3://alpacapps/langbang/` R2 prefix was deleted.
- Unrelated `alpacapps` bucket prefixes were not touched.
- The `langbang.org` zone/site remains on the Wingsiebird Cloudflare account,
  but live `https://langbang.org/builds` points to new-account Worker/R2 assets
  and had zero old Worker/R2 references during verification.

## Old Worker

Target:

- Account ID: `9cd3a280a54ce2a5b382602f0247b577`
- Worker: `langbangml-api`
- URL: `https://langbangml-api.alpacapps.workers.dev`

Actions and results:

- Pre-delete Worker script GET returned HTTP 200.
- DELETE
  `https://api.cloudflare.com/client/v4/accounts/9cd3a280a54ce2a5b382602f0247b577/workers/scripts/langbangml-api`
  returned HTTP 200 with `success=true`.
- Post-delete Worker script GET returned HTTP 404:
  `This Worker does not exist on your account.`
- Public old Worker health URL returned HTTP 404:
  `https://langbangml-api.alpacapps.workers.dev/health`.

## Old D1

Target:

- D1 database: `langbangml`
- D1 database ID: `0c56de2d-285a-4f72-b629-29b508eb8348`

Actions and results:

- Pre-delete D1 database GET returned HTTP 200 for `langbangml`.
- DELETE
  `https://api.cloudflare.com/client/v4/accounts/9cd3a280a54ce2a5b382602f0247b577/d1/database/0c56de2d-285a-4f72-b629-29b508eb8348`
  returned HTTP 200 with `success=true`.
- Post-delete D1 database GET returned HTTP 404:
  `The database 0c56de2d-285a-4f72-b629-29b508eb8348 could not be found`.

Old D1 row counts from the arrival verification before cleanup:

| Table | Old rows |
| --- | ---: |
| `language_pairs` | 2 |
| `app_instances` | 2 |
| `ui_labels` | 66 |
| `content_versions` | 2 |
| `content_lessons` | 7 |
| `audio_assets` | 7759 |
| `content_audio_requirements` | 0 |
| `sync_events` | 0 |

The final pre-delete row-count query attempted during cleanup failed with
SQLite `too many terms in compound SELECT`; the database existence and deletion
checks succeeded.

## Old R2

Target:

- Bucket: `alpacapps`
- Prefix: `langbang/`
- Public base: `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev`

Pre-delete inventory:

- Objects: 54,796
- Bytes: 1,278,259,463
- Decimal GB: 1.278
- Binary GiB: 1.190

Deletion method:

- A slow `aws s3 rm s3://alpacapps/langbang/ --recursive` was stopped after
  running too slowly.
- Cleanup then used exact-key `aws s3api delete-objects` batches from
  `docs/cloudflare-migration/old-r2-langbang-key-manifest.json`.
- Batch delete requested deletion for all 54,796 keys.
- Final old-prefix list returned:
  `{"KeyCount":0,"IsTruncated":null,"Contents":null}`.

Inventory artifacts:

- Full old key manifest:
  `docs/cloudflare-migration/old-r2-langbang-key-manifest.json`
- Missing-key diff:
  `docs/cloudflare-migration/old-r2-missing-in-new.keys`
- Size mismatch diff:
  `docs/cloudflare-migration/old-r2-size-mismatches.tsv`
- ETag mismatch diff:
  `docs/cloudflare-migration/old-r2-etag-mismatches.tsv`

Pre-delete old-vs-new R2 comparison:

- Missing old keys in new bucket: 0
- Size mismatches: 1
- ETag mismatches: 5

The only size mismatch was mutable `langbang/langbang-latest.apk`, which had
changed after newer publishes. ETag-only mismatches were recorded in the TSV
artifact.

## Post-Cleanup New-Account Verification

The following live URLs returned HTTP 200 with zero old-account references:

- `https://langbangml-api.langbangml.workers.dev/health`
- `https://langbangml-api.langbangml.workers.dev/v1/instances`
- `https://langbangml-api.langbangml.workers.dev/v1/instances/langbangml-en-pl/bootstrap`
- `https://langbangml-api.langbangml.workers.dev/v1/instances/langbangml-pl-en/bootstrap`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/langbang-latest.json`
- `https://alpacaplayhouse.com/rahulio/pages/langbang/`
- `https://alpacaplayhouse.com/rahulio/pages/langbang/builds.html`
- `https://langbang.org/builds`

Post-cleanup LangBangML manifests:

- `en-pl`: `versionCode=325`, `versionName=0.1.8.325`
- `pl-en`: `versionCode=325`, `versionName=0.1.8.325`

Post-cleanup original LangBang update manifest:

- `versionCode=316`, `versionName=0.1.8.316`

Tablet installed APKs checked before cleanup no longer embedded old Cloudflare
URLs:

- `com.sponic.langbang`: `0.1.8.317`
- `com.sponic.langbangml.enpl`: `0.1.8.321`
- `com.sponic.langbangml.plen`: `0.1.8.322`

Later concurrent LangBangML publishes advanced live manifests to v325.

## Remaining Old-Account References

Active runtime/config/page scans found no old `alpacapps.workers.dev` Worker
references.

Remaining old account references are retained for history/safety in:

- Migration prompts.
- Migration evidence files.
- The original LangBang `scripts/publish-r2.sh` refusal guard and old-URL page
  replacement rule.

## Bitwarden

No Bitwarden items were deleted. The following items were used for cleanup:

- `Cloudflare — LangBangML Worker Deploy`
- `Cloudflare R2 — Object Storage`
- `Cloudflare R2 - LangBang S3 Admin`
- `Cloudflare — LangBangML Content API — New Account`

Secret values were not written to this file.
