# LangBangML — Project Directives

LangBangML is the Cloudflare-backed LangBang fork at:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

## Source Of Truth

Use this checkout for all new LangBang work. When the user says "LangBang",
interpret that as LangBangML unless they explicitly say "legacy LangBang",
"old bundled app", or `/Users/rahulio/Documents/CodingProjects/langbang`.

Do not route content, phrases, audio, builds, releases, Cloudflare, or tablet
work through the older bundled-asset repo. If a task starts in that repo, switch
back here before acting.

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

The publisher runs the worktree integrity gate before building or publishing.

The legacy `scripts/publish-r2.sh` is intentionally disabled in this fork.

For local tablet iteration, build the changed flavor and install it on the
Samsung tablet when `100.103.110.7:5555` is connected.

Before any tablet-facing build/install, run the worktree integrity gate:

```bash
scripts/check-worktree-integrity.sh --allow-current-dirty
```

Reconcile dirty or ahead changes in other LangBangML worktrees before installing
over the tablets. See `docs/process/tablet-build-integrity.md`.

## Backend Helpers

- Content API helper: `scripts/langbangml-content-api.sh`
- Worker source: `cloudflare/langbangml/src/index.js`
- Worker config: `cloudflare/langbangml/wrangler.toml`
- Migration evidence: `docs/cloudflare-migration/`
- Cleanup prompts: `docs/prompts/`

Admin tokens and new Cloudflare operational credentials belong in Bitwarden
collection `devops-langbang`.

## Bitwarden Recipe

In non-interactive Codex shells, `bw unlock` and scripts that wrap
`~/bin/bw-unlock` can crash at the master-password prompt with
`ERR_USE_AFTER_CLOSE`. Seed `BW_SESSION` from the macOS keychain first, then run
the helper script. Keep output restricted to IDs, counts, and status; never
print secret values.

```bash
export BW_PASSWORD="$(security find-generic-password -a "rahulioson@gmail.com" -s "bitwarden-cli" -w)"
export BW_SESSION="$(/opt/homebrew/bin/bw unlock --passwordenv BW_PASSWORD --raw)"
unset BW_PASSWORD

# Example: verify access without printing the secret.
bw get item "Cloudflare R2 - LangBang S3 Admin" --session "$BW_SESSION" \
  | jq -r '.id, .name'
```

## Cleanup Boundary

Do not delete Cloudflare resources unless the user explicitly asks for cleanup.
Before any old-account cleanup, follow
`docs/prompts/02-cleanup-old-langbang-cloudflare-account.md`.
