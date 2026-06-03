# LangBangML New Cloudflare Account Arrival Verification

Date: 2026-06-03

## New Cloudflare Resources

- Account ID: `df99afea5ab9636a19adbdead37fc133`
- Account name from API: `Langbangapp@gmail.com's Account`
- Worker name: `langbangml-api`
- Worker URL: `https://langbangml-api.langbangml.workers.dev`
- Worker version verified after deploy: `851b844a-7fd1-446c-8460-7bfc615fb7c0`
- D1 database name: `langbangml`
- D1 database ID: `d259445e-d263-4ae2-a391-f0d176492265`
- R2 bucket: `langbangml`
- Public R2 base: `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`
- R2 S3 endpoint: `https://df99afea5ab9636a19adbdead37fc133.r2.cloudflarestorage.com`

## Old Cloudflare Resources Left In Place

- Old account ID: `9cd3a280a54ce2a5b382602f0247b577`
- Old Worker URL: `https://langbangml-api.alpacapps.workers.dev`
- Old D1 database ID: `0c56de2d-285a-4f72-b629-29b508eb8348`
- Old R2 bucket: `alpacapps`
- Old public R2 base: `https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev`

No old-account D1 databases, R2 buckets, R2 objects, Workers, or `langbang.org`
zone resources were deleted.

## D1 Copy Verification

| Table | Old rows | Copied rows | New rows |
| --- | ---: | ---: | ---: |
| `language_pairs` | 2 | 2 | 2 |
| `app_instances` | 2 | 2 | 2 |
| `ui_labels` | 66 | 66 | 66 |
| `content_versions` | 2 | 2 | 2 |
| `content_lessons` | 7 | 7 | 7 |
| `audio_assets` | 7759 | 7759 | 7759 |
| `content_audio_requirements` | 0 | 0 | 0 |
| `sync_events` | 0 | 0 | 0 |

After the copy, all `audio_assets.public_url` values were rewritten to the new
R2 public base:

- `audio_assets` total: 7759
- New-base URLs: 7759
- Old-base URLs: 0

Copy helper summary: `/tmp/langbangml-migration/d1-copy-summary.json`

## R2 Copy Verification

The old account changed by 7 objects while the first R2 copy was running, so an
incremental second pass was run before final verification.

| Prefix | Old account | New account |
| --- | ---: | ---: |
| `langbang/` objects | 54,796 | 54,796 |
| `langbang/` bytes | 1,278,259,463 | 1,278,259,463 |

Final count files:

- `/tmp/langbangml-migration/r2-old-size-final.txt`
- `/tmp/langbangml-migration/r2-new-size-final.txt`

After publishing the first two fresh app channels and build pages, the new
`langbang/` prefix intentionally diverged from the old copied-state count. A
later v317 publish/install run added newer pinned APKs and replaced latest APKs,
so the new-account prefix is expected to remain larger than the copied old
prefix:

| Prefix state | Old account | New account |
| --- | ---: | ---: |
| Post-publish `langbang/` objects | 54,796 | 54,804 |
| Post-publish `langbang/` bytes | 1,278,259,463 | 1,535,866,037 |

Sample new-account public audio HEAD checks returned HTTP 200 with
`audio/mpeg`:

- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/audio/0003515fd80197e9d77cae053c5f820cb18c1013.mp3`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/audio/0009b1f20e7d593ad5a4a5f84a4c3d1b4e26cfe1.mp3`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/audio/00175906c61ebd387df61baaad6464eb60867519.mp3`

## Worker Endpoint Verification

- `GET /health`: `ok=true`, `service=langbangml-api`
- `GET /v1/instances`: 2 instances, `langbangml-pl-en` and `langbangml-en-pl`
- `GET /v1/instances/langbangml-en-pl/bootstrap`:
  - pair: `en-pl`
  - source: English
  - target: Polish
  - content: `en-pl-v1`
  - lessons: 6
  - labels: 33
  - public R2 base: `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`
- `GET /v1/instances/langbangml-pl-en/bootstrap`:
  - pair: `pl-en`
  - source: Polish
  - target: English
  - content: `pl-en-v1`
  - lessons: 1
  - labels: 33
  - public R2 base: `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`

## Protected Admin API Verification

`scripts/langbangml-content-api.sh GET /v1/admin/content/en-pl-v1/lessons`
returned 6 lessons:

- `lesson-01`
- `lesson-02`
- `lesson-03`
- `lesson-04`
- `lesson-05`
- `lesson-06`

## Audio Warm Dry-Run Verification

- `POST /v1/admin/content/en-pl-v1/audio/warm-missing` with `dryRun=true`:
  - total required: 4305
  - missing: 1749
  - sample returned: 10
- `POST /v1/admin/content/pl-en-v1/audio/warm-missing` with `dryRun=true`:
  - total required: 140
  - missing: 0
  - sample returned: 0

The Worker was patched before the final dry run so it computes missing audio
from D1 metadata first, then only performs R2/Azure work for actually missing
items. This avoids the subrequest/runtime limit hit when HEAD-checking every
required audio asset.

## Client And Build Page Verification

Android client config was changed to use:

- API base: `https://langbangml-api.langbangml.workers.dev`
- EN to PL update manifest:
  `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json`
- PL to EN update manifest:
  `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json`

Two APK flavors were built and published. The latest synced publish/install run
is v317:

| Channel | Package | Instance | Version | Latest APK |
| --- | --- | --- | --- | --- |
| English speakers learning Polish | `com.sponic.langbangml.enpl` | `langbangml-en-pl` | `0.1.8.317` / code `317` | `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/langbangml-en-pl-latest.apk` |
| Polish speakers learning English | `com.sponic.langbangml.plen` | `langbangml-pl-en` | `0.1.8.317` / code `317` | `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/langbangml-pl-en-latest.apk` |

Published manifests:

- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json`
- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json`

Published fallback builds page:

- `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/index.html`

`https://langbang.org/builds` was updated to site version `site-v2` and verified
live with both tabs:

- English speakers learning Polish
- Polish speakers learning English

The `langbang.org` zone is still in the old Wingsiebird Cloudflare account, so
the public site Worker route remains there for now. The APKs, manifests, Worker
API, D1, and R2 assets used by the page are in the new Cloudflare account.

Tablet install verification:

- Device: `100.103.110.7:5555`
- Removed stale unflavored package `com.sponic.langbangml`
- Installed `com.sponic.langbangml.enpl`: version code `317`, version name
  `0.1.8.317`
- Installed `com.sponic.langbangml.plen`: version code `317`, version name
  `0.1.8.317`
- `cloud-config.xml` for `com.sponic.langbangml.enpl` selected
  `langbangml-en-pl`
- `cloud-config.xml` for `com.sponic.langbangml.plen` selected
  `langbangml-pl-en`
- Screenshot sanity check:
  `/Users/rahulio/Documents/Screenshotz/langbangml-v317-enpl-20260603-080053.png`
