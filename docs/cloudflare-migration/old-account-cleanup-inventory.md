# Old Cloudflare Account Cleanup Inventory

Date: 2026-06-03

This inventory was captured before destructive cleanup of old Wingsiebird /
alpacapps Cloudflare resources for LangBang and LangBangML.

## Old Resources Targeted

- Account ID: `9cd3a280a54ce2a5b382602f0247b577`
- Worker: `langbangml-api`
- Worker URL: `https://langbangml-api.alpacapps.workers.dev`
- D1 database: `langbangml`
- D1 database ID: `0c56de2d-285a-4f72-b629-29b508eb8348`
- R2 bucket: `alpacapps`
- R2 prefix: `langbang/`
- Old public R2 base: `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev`

## New Account Safety Evidence

- New Worker `https://langbangml-api.langbangml.workers.dev/health` returned
  HTTP 200 with `ok=true`.
- New Worker `/v1/instances` returned 2 instances:
  `langbangml-pl-en`, `langbangml-en-pl`.
- New bootstrap endpoints returned HTTP 200 and only the new R2 public base.
- Protected content API returned 6 lessons for `en-pl-v1` and 6 lessons for
  `pl-en-v1`.
- New R2 sample audio URLs returned HTTP 200.
- New LangBangML build manifests returned HTTP 200 for both directions:
  `versionCode=321`.
- Original LangBang update manifest returned HTTP 200 from the new R2 base:
  `versionCode=316`.
- Live pages returned HTTP 200 with zero old R2 / old Worker references:
  `https://alpacaplayhouse.com/rahulio/pages/langbang/`,
  `https://alpacaplayhouse.com/rahulio/pages/langbang/builds.html`, and
  `https://langbang.org/builds`.
- Tablet installed packages no longer embed old Cloudflare URLs:
  - `com.sponic.langbang`: `0.1.8.317`
  - `com.sponic.langbangml.enpl`: `0.1.8.321`
  - `com.sponic.langbangml.plen`: `0.1.8.322`

## R2 Inventory

Old `s3://alpacapps/langbang/` before cleanup:

- Objects: 54,796
- Bytes: 1,278,259,463
- Decimal GB: 1.278
- Binary GiB: 1.190

New `s3://langbangml/langbang/` at readiness comparison:

- Objects: 55,890
- Bytes: 2,269,859,946
- Decimal GB: 2.270
- Binary GiB: 2.114

Old-vs-new R2 comparison:

- Missing old keys in new bucket: 0
- Size mismatches: 1
- ETag mismatches: 5

Mismatch notes:

- `langbang/langbang-latest.apk` and `langbang/langbang-latest.json` are mutable
  latest objects and intentionally differ after newer publishes.
- `langbang/langbang-v294-arm64.apk`, `langbang/langbang-v295-arm64.apk`, and
  `langbang/langbang-v296-arm64.apk` had matching sizes and differing ETags.

Inventory artifacts:

- Full old R2 key manifest:
  `docs/cloudflare-migration/old-r2-langbang-key-manifest.json`
- Missing-key diff:
  `docs/cloudflare-migration/old-r2-missing-in-new.keys`
- Size mismatch diff:
  `docs/cloudflare-migration/old-r2-size-mismatches.tsv`
- ETag mismatch diff:
  `docs/cloudflare-migration/old-r2-etag-mismatches.tsv`

## Active Reference Scan

Active repo scan found no old-account references in runtime clients, active
LangBangML publish scripts, live LangBang pages, or langbang Supabase helper
functions.

Remaining active references before cleanup were only in the original LangBang
`scripts/publish-r2.sh` old-account refusal guard and page URL migration
replacement:

- `9cd3a280a54ce2a5b382602f0247b577`
- `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev`

Historical references remain in migration prompts/evidence and memory files.

## Bitwarden Items Consulted

- Old Worker/D1 token item:
  `Cloudflare — LangBangML Worker Deploy`
- Old R2 S3 item:
  `Cloudflare R2 — Object Storage`
- New R2 S3 item:
  `Cloudflare R2 - LangBang S3 Admin`
- New content API item:
  `Cloudflare — LangBangML Content API — New Account`

Secret values were not written to this file.
