# LangBangML — Project Directives

LangBangML is the Cloudflare-backed LangBang fork at:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

It is separate from the original bundled-asset Android app in
`/Users/rahulio/Documents/CodingProjects/langbang`.

## Cloudflare

LangBangML uses the new LangBang Cloudflare account:

- Worker: `https://langbangml-api.langbangml.workers.dev`
- D1 database: `langbangml`
- R2 bucket: `langbangml`
- Public R2 base: `https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev`
- Main R2 prefix: `langbang/`

Do not publish LangBangML artifacts to the old `alpacapps` R2 bucket. The old
account values may remain only in migration evidence or cleanup prompts.

## Build And Publish

The app has two debug APK flavors:

- `enPl`: package `com.sponic.langbangml.enpl`, instance `langbangml-en-pl`
- `plEn`: package `com.sponic.langbangml.plen`, instance `langbangml-pl-en`

Use the flavor-aware publisher:

```bash
scripts/publish-langbangml-builds.sh
```

The legacy `scripts/publish-r2.sh` is intentionally disabled in this fork.

For local tablet iteration, build the changed flavor and install it on the
Samsung tablet when `100.103.110.7:5555` is connected.

## Backend Helpers

- Content API helper: `scripts/langbangml-content-api.sh`
- Worker source: `cloudflare/langbangml/src/index.js`
- Worker config: `cloudflare/langbangml/wrangler.toml`
- Migration evidence: `docs/cloudflare-migration/`
- Cleanup prompts: `docs/prompts/`

Admin tokens and new Cloudflare operational credentials belong in Bitwarden
collection `devops-langbang`.

## Cleanup Boundary

Do not delete Cloudflare resources unless the user explicitly asks for cleanup.
Before any old-account cleanup, follow
`docs/prompts/02-cleanup-old-langbang-cloudflare-account.md`.
