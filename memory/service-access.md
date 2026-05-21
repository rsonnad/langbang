# Service access recipes (langbang)

## Cloudflare R2 — duplicate BW item cleanup — 2026-05-20

**Root cause fixed today.** There were two BW items both named
`Cloudflare R2 — Object Storage`:
- `3212e180-6521-4537-b89e-b410007ea3a7` — populated (11 fields incl.
  Access Key ID, Secret Access Key, Account ID, S3 API, Public Dev URL).
- `d886c589-f3ae-462e-8ddc-b411014aadc8` — empty placeholder created
  2026-03-18. Now **deleted**.

Symptom before delete: `bw get item "Cloudflare R2 — Object Storage"`
returned `"More than one result was found"` and `bw-read "Cloudflare R2 — Object Storage" "Access Key ID"` printed
`✗ Field 'Access Key ID' not found` (resolves to the empty dup first).
`scripts/publish-r2.sh` depends on the canonical name resolving uniquely,
so this was load-bearing.

**Heads-up:** `Cloudflare R2 — Finleg Object Storage` has the same
empty-dup pattern (`4e9b92de-...` populated, `ec244f20-...` empty).
Same one-line fix when next encountered:
`bw delete item ec244f20-1fd0-4039-bd57-b411014b958e --session "$BW_SESSION"`.

## Supabase (alpacapps project, langbang schema) — 2026-05-20

**Always run BW commands inline.** `bw-read` emits a `_bw_log: command not found`
warning to stderr but still prints the value on stdout, so the inline form
`$(bw-read "Supabase — AlpacApps Project" "psql Password" | python3 -c '...')`
works fine — that stderr noise is cosmetic.

**Management API PAT is STALE as of 2026-05-20.** Field `Management API Token`
on BW item `Supabase — AlpacApps Project` is `sbp_3e69f8663424...2db5` and
returns `{"message":"Unauthorized"}` on every endpoint including
`/v1/projects`. **Fix at root:** rotate at
[supabase.com/dashboard/account/tokens](https://supabase.com/dashboard/account/tokens),
update the BW field. Until then, use psql for SQL and the SQL-side trick
below for PostgREST config — both are documented in
`genalpaca-admin/docs/CREDENTIALS.md`.

**Working psql recipe (verbatim from alpacapps CREDENTIALS.md):**
```bash
export BW_SESSION=$(~/bin/bw-unlock)
/opt/homebrew/opt/libpq/bin/psql "postgres://postgres.aphrrfprbixmhissnjfn:$(bw-read "Supabase — AlpacApps Project" "psql Password" | python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.stdin.read().strip()))')@aws-1-us-east-2.pooler.supabase.com:6543/postgres?sslmode=require&gssencmode=disable" -c "SQL HERE"
```
The `?` + `&` in the URL only work because of inline substitution into the
double-quoted DSN; if you split into env vars or `-f` with the DSN as a
separate arg, libpq sometimes drops the password and re-prompts. Stick to
the inline form.

**Expose a new schema to PostgREST without Management API:**
```sql
ALTER ROLE authenticator SET pgrst.db_schemas = 'public,graphql_public,<new>';
NOTIFY pgrst, 'reload config';
```
Verify with `curl ... -H "Accept-Profile: bogus"` against `/rest/v1/<table>`
— the error lists currently-exposed schemas. This is how `langbang` got
exposed on 2026-05-20 (the Studio dashboard toggle is the documented way
but requires the dead PAT or a manual click).

## Wireless adb pairing — Pixel 10 Pro XL — 2026-05-18

**Working recipe (Tailscale path):** pair + connect both over the Pixel's tailnet IP.

```bash
# 1. Confirm adb server is healthy and Pixel is on the tailnet:
adb devices                           # should not hang
tailscale status | grep pixel         # gets Pixel's 100.x IP
# 2. On phone: Developer options → Wireless debugging → Pair device with pairing code.
#    Leave the dialog open. Read the rotating pair port + 6-digit code.
adb pair 100.94.27.79:<pair-port> <6-digit>
# 3. Then connect on the persistent adb port (shown on main Wireless debugging screen):
adb connect 100.94.27.79:<adb-port>
```

Pixel 10 Pro XL identifies as `product:mustang model:Pixel_10_Pro_XL device:mustang`, guid `adb-57091FDCQ0081F-...`.

**LAN pair failure mode encountered today:** `adb pair 192.168.1.181:<pair-port> <code>` returned `protocol fault (couldn't read status message): Undefined error: 0` on four consecutive fresh codes. Root cause was a **stuck `adb connect` to a previously-quit Tailscale peer** (Tab A9+ at `100.103.110.7:34975`) wedging the adb server. The stuck process resolved itself when Tailscale was re-enabled on the Mac — adb 37.0.0 was fine all along.

**If you see this error again:**
```bash
pgrep -lf adb                         # look for stuck `adb connect`
lsof -i :5037                         # check adb server state
adb kill-server && adb start-server   # if any stuck adb processes found
```
…then retry pair (over Tailscale if available — bypasses any LAN mDNS/firewall weirdness too).

Do **not** waste time on Android Studio QR pairing for this device — CLI pair works once adb state is clean.

**Daily-use wrapper:** `~/bin/adb-pixel` (mirrors `~/bin/adb-tab`). Caches the working port in `~/.cache/adb-pixel/port`; if stale, nmap-scans `100.94.27.79` ports 30000-50000 for the rotated wireless-debug listener. Pairing is durable across reboots/toggle-cycles — only the port number rotates, which the wrapper hides. Override host via `ADB_PIXEL_HOST=<ip>`.

## Azure Speech / Cognitive Services TTS — PROVISIONED 2026-05-18

**BW item:** `Azure Speech — langbang-speech (TTS)` — id `3d26dda5-fea1-4362-b30c-b44f001e860c` in collection `DevOps-sponicgarden` (`4be0758e-1d91-48c2-8275-b416005ff93f`).

**Resource:** `langbang-speech` in RG `sponic-ai`, region `eastus`, SKU `S0`, subscription `785e237b-a779-4da4-bcfb-2c2ab687aa0b`.

**Polish voices available (standard Neural, top quality for Polish):**
- `pl-PL-AgnieszkaNeural` (F)
- `pl-PL-MarekNeural` (M)
- `pl-PL-ZofiaNeural` (F)

(Azure does not currently surface "Neural HD" variants for Polish in eastus — these standard Neural voices are the production-quality option. Billing is $16/1M chars, not $30.)

### Fetch the key

```bash
export BW_SESSION=$(~/bin/bw-unlock)
ITEM=$(bw get item 3d26dda5-fea1-4362-b30c-b44f001e860c --session "$BW_SESSION")
AZURE_SPEECH_KEY=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="key1") | .value')
AZURE_SPEECH_REGION=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="region") | .value')
```

### Quick test (list Polish voices)

```bash
curl -s -H "Ocp-Apim-Subscription-Key: $AZURE_SPEECH_KEY" \
  "https://${AZURE_SPEECH_REGION}.tts.speech.microsoft.com/cognitiveservices/voices/list" \
  | jq '.[] | select(.Locale=="pl-PL")'
```

### Synthesize speech (POST SSML)

Endpoint: `https://${AZURE_SPEECH_REGION}.tts.speech.microsoft.com/cognitiveservices/v1`
Header: `Ocp-Apim-Subscription-Key: $AZURE_SPEECH_KEY`
Header: `Content-Type: application/ssml+xml`
Header: `X-Microsoft-OutputFormat: audio-24khz-48kbitrate-mono-mp3`

## Service Principal (claude-code-sp) — for provisioning more resources

BW item id: `40b98339-3dce-4cec-9eaf-b43f00f43e80`. Has Contributor on full subscription.

**Gotcha (2026-05-18):** `bw get item` returns JSON with raw `\n` characters in the `notes` field when notes are multi-line, which breaks both `python -m json` and `jq`. Workaround: extract fields directly with `jq` only when notes are single-line, OR delete/replace problematic notes, OR strip control chars first: `tr -d '\000-\037' | jq ...`.

```bash
export BW_SESSION=$(~/bin/bw-unlock)
ITEM=$(bw get item 40b98339-3dce-4cec-9eaf-b43f00f43e80 --session "$BW_SESSION" | tr -d '\000-\037')
TENANT=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="tenant_id") | .value')
SUB=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="subscription_id") | .value')
CID=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="client_id") | .value')
SECRET=$(echo "$ITEM" | jq -r '.fields[] | select(.name=="client_secret") | .value')
az login --service-principal -u "$CID" -p "$SECRET" --tenant "$TENANT" --output none
az account set --subscription "$SUB"
```

## Google Gemini — SponicGardens — USED 2026-05-18 (langbang add-verb)

**BW item:** `c4b16931-335b-457a-9910-b416006d3b8c` in `DevOps-sponicgarden`.
**Gotcha:** the API key lives in `login.password`, NOT in a custom `fields` entry. The `fields` array only has metadata (project name, dashboard URL, key suffix `...6cIU`, billing tier). Looking only at `fields[]` makes you think the key is missing.

```bash
export BW_SESSION=$(~/bin/bw-unlock)
GEMINI_API_KEY=$(bw get item c4b16931-335b-457a-9910-b416006d3b8c --session "$BW_SESSION" \
  | tr -d '\000-\037' | jq -r '.login.password')
```

Used by `langbang` for English→Polish verb translation + conjugation via REST
`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY`
with `generationConfig.responseMimeType=application/json` to force JSON-only output.
Wired into `local.properties` as `GEMINI_API_KEY=…` → BuildConfig field.

## Other provisioned services relevant to langbang

- **Deepgram** — Polish STT candidate (pronunciation feedback). BW: `c8a5e31d-...` (verify in DevOps-sponicgarden).
- **ElevenLabs** — alt TTS, BW: `cb350054-4ba0-4604-861a-b44a00393240`.

## Android Wireless Debugging (Galaxy Tab A9+) — recipe 2026-05-18

Target device: Galaxy Tab A9+ Wi-Fi, **model SM-X210**, **Android 16**, reachable over Tailscale at `100.103.110.7` (Tailscale runs on the tablet). `adb` is at `/opt/homebrew/bin/adb`. Successful pairing GUID: `adb-R95Y301R05A-4CHDdE` (persists across reconnects).

**Pairing flow (two ports, easy to confuse):**

1. On tablet: Settings → Developer options → Wireless debugging → **"Pair device with pairing code"**. Screen shows a **pairing port** + **6-digit code**.
2. The main Wireless debugging screen shows a separate **connect port** — different number, can also rotate when entering/leaving the pairing screen, so always re-check after pairing.

**Gotchas (hit 2026-05-18):**
- The pairing code/port expire **fast** (~30–60s after the screen appears, and immediately if you leave the screen). Two attempts failed with `error: protocol fault (couldn't read status message)` because the code was stale by the time we ran `adb pair`. Net: keep pairing screen open, paste code, run command with no delay.
- The connect port also rotates — a stale connect port returns `Connection refused`. After pairing succeeds, back out to the main Wireless debugging screen and re-read "IP address & Port" before running `adb connect`.

```bash
# Pair (fresh port + code each time):
adb pair 100.103.110.7:<PAIRING_PORT> <6-DIGIT-CODE>

# Connect (port is the stable one from the main Wireless debugging screen):
adb connect 100.103.110.7:<CONNECT_PORT>
adb devices
```

Future reconnects on the tailnet only need `adb connect` — pairing is persistent across sessions until the tablet revokes the key.

**Wrapper:** `~/bin/adb-tab` auto-handles the rotating connect port — caches the last-known port, falls back to an `nmap -p 30000-65535 -T4 --min-rate 2000 --host-timeout 90s` scan to find the current one, then tries each candidate until one returns a real `device`. Usage: just `adb-tab`. Requires `nmap` (already installed at `/opt/homebrew/bin/nmap`).

**Additional gotcha:** Wireless debugging toggle is easy to flip off accidentally during other tablet activity (app installs, screen unlocks, etc.). If `adb-tab` reports "no open high ports" but the tablet is pingable, verify the toggle is on (Settings → Developer options → Wireless debugging).

**Tuning history — 2026-05-20:** wrapper originally scanned with `-T5` (extreme timing) on range 30000-50000. On Tailscale's ~80ms RTT, `-T5` drops SYN/ACKs and silently misses open ports — observed missing port 42231 (in-range) on a real reconnect. Switched to `-T4`, widened range to 30000-65535 (Android picks anywhere in the ephemeral range), and bumped `--host-timeout` from 30s to 90s. Don't revert to `-T5` even though it looks faster.

## "tab screenshot" recipe — Galaxy Tab A9+ — 2026-05-19

**Phrase trigger:** when the user says **"tab screenshot"**, **"screenshot the tab"**, **"screenshot the tablet"**, or any close variant, ALWAYS run `~/bin/adb-tab-screenshot` to capture from the Samsung SM-X210 (Galaxy Tab A9+) at `100.103.110.7` — never the iOS Simulator, never an Android emulator, never the Pixel 10 Pro XL (also on adb at `100.94.27.79`). The wrapper auto-reconnects via `adb-tab` if needed.

```bash
~/bin/adb-tab-screenshot
# prints: /tmp/tab-screenshots/tab-YYYYMMDD-HHMMSS.png
```

Then `Read` the returned path to view the image inline. Override the output dir with `ADB_TAB_SCREENSHOT_DIR=…` or the host with `ADB_TAB_HOST=…`.

**Why this recipe exists:** the Mac has multiple adb devices and historically multiple emulators — without the recipe, every "screenshot" request kicks off a clarification round-trip. The Galaxy Tab is the canonical build target for this project.

## ALPUCA SSH (Tailscale, for langbang backup target) — 2026-05-18

On the Sponic tailnet ALPUCA is `100.74.59.97`, hostname `alpuca`, login user `alpuca` (not `paca` as some older `~/.ssh/config` snippets show — `whoami` confirms `alpuca`). `~/.ssh/config` already has a `Host alpuca` block, so plain `ssh alpuca 'whoami'` works from any tailnet-connected Mac.

Backup destination for langbang lives behind a symlink so the Android app's default `RVAULT/backups/langbang` relative path resolves correctly:

```bash
ssh alpuca 'ln -s /Volumes/RVbackup ~/RVAULT 2>/dev/null; mkdir -p ~/RVAULT/backups/langbang'
```

The physical volume is `/Volumes/RVbackup` (exFAT, external). `/Volumes/rvault20` is a different (primary) vault used for media/dedup work — do NOT redirect langbang backups there.

Cloudflare-Access fallback for off-tailnet SSH: BW `Cloudflare Access — Alpuca SSH Service Token` (`16741e80-3e26-4bf8-8154-b422017cd095`, DevOps-shared) — Client ID `1c010bd879b5f0862c57afd18e5a1053.access`, app `Alpuca SSH` at `ssh.alpacaplayhouse.com`. Use `ssh alpuca-cf` from `~/.ssh/config` (`ProxyCommand cloudflared access ssh --hostname %h`).

## ADB on-device file plumbing — gotchas (2026-05-18)

Hit while wiring the langbang Backup feature; record so the next session skips the trial-and-error:

**Pulling private files out of an app's filesDir:** `adb shell run-as <pkg> cat <path>` via a host shell redirect (`> file`) silently produces an empty file because the inner-shell stdout is mediated by `adb shell`'s line-buffered TTY. Use `adb exec-out` instead — it gives you the raw byte stream:

```bash
adb exec-out run-as com.sponic.langbang cat files/ssh/id_rsa > /tmp/key
chmod 600 /tmp/key && head -1 /tmp/key   # -----BEGIN RSA PRIVATE KEY-----
```

**Writing into an app's `shared_prefs/` from the host:** `run-as` can't read `/sdcard`, so the `adb push /sdcard/foo.xml → run-as cp` path fails with `Permission denied`. Pipe the content over adb's stdin into a `run-as` shell instead:

```bash
adb shell "run-as com.sponic.langbang sh -c 'cat > shared_prefs/langbang_backup.xml'" < local.xml
```

Force-stop the app first (`am force-stop <pkg>`) or the SharedPreferences in-memory cache will overwrite your file on the next `apply()`. Re-launch with `am start -n <pkg>/.MainActivity` to pick up the new values.

**Inspecting Java-produced ZIPs on macOS:** BSD `unzip -l` reports `0 files` on archives written by `java.util.zip.ZipOutputStream` because Java uses streaming data descriptors instead of writing entry sizes in local file headers. Use `zipinfo`, `jar tf`, or Python `zipfile` — all read them correctly.

**Driving Compose UI via adb tap:** `uiautomator dump` gives reliable bounds for Compose Material3 tabs and buttons (look for `clickable="true"` ancestors of the visible text). Coordinates DO shift if `systemBarsPadding()` is added to the root layout, so re-dump after any UI change. `adb shell input swipe 960 900 960 200 400` scrolls one viewport-height on a 1920×1200 landscape tablet — usually need two swipes to reach the bottom card on the Settings screen.

## R2 publish — langbang APK distribution — 2026-05-20

**Canonical release channel.** APKs go to the alpacapps R2 bucket under `langbang/`. Do NOT use GitHub Releases — slow upload (3+ min for 60 MB, often crashes), and the user prefers R2.

**One command:**
```bash
./scripts/publish-r2.sh
```
Builds, uploads `langbang-v{N}-arm64.apk` + `langbang-latest.apk`, verifies both URLs return 200, patches `genalpaca-admin/rahulio/pages/langbang/index.html` with the new version label.

**Raw recipe (if the script breaks):**
```bash
export BW_SESSION=$(~/bin/bw-unlock)
ITEM=$(bw get item "Cloudflare R2 — Object Storage" --session "$BW_SESSION")
export AWS_ACCESS_KEY_ID=$(jq -r '.fields[]|select(.name=="Access Key ID")|.value' <<<"$ITEM")
export AWS_SECRET_ACCESS_KEY=$(jq -r '.fields[]|select(.name=="Secret Access Key")|.value' <<<"$ITEM")
export AWS_DEFAULT_REGION=auto
ENDPOINT="https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com"
aws s3 cp app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
  s3://alpacapps/langbang/langbang-v${N}-arm64.apk \
  --endpoint-url "$ENDPOINT" --content-type application/vnd.android.package-archive --no-progress
```

**Gotchas observed this session:**
- The BW item ID is `3212e180-6521-4537-b89e-b410007ea3a7` in DevOps-alpacapps. A copy exists in DevOps-shared too — either works; `bw get item "Cloudflare R2 — Object Storage"` by name resolves across collections.
- `aws s3 cp --quiet` swallows stderr on multipart failures and reports exit 0 even when the upload aborted mid-stream. Always use `--no-progress` instead of `--quiet`, and verify with `curl -sI <public-url> | head -1` before declaring success.
- Multipart progress display resets per-chunk: speed appears to "crash" from 5 MB/s → 200 KB/s every 8 MB. Not a real slowdown, just the AWS CLI counter resetting per part.
- The public-dev URL has propagation lag of a few seconds after upload — first HEAD after `cp` can 404; retry once before declaring failure.

**Android download-then-hang on Gmail:** Not an R2 problem. APK content bytes from R2 verified intact (SHA-256 matches local APK). The hang is Android refusing to hand a Gmail download off to Package Installer because Gmail isn't trusted as an install source. Fix: Settings → Apps → Special access → Install unknown apps → Gmail → Allow. Chrome works because the user already trusted it.

**Additional gotchas — 2026-05-20 (v34/v35 upload):**
- `--region auto` is REQUIRED, not optional. R2 rejects calls without it (or with an inferred AWS region) with cryptic signature errors.
- **Multipart upload drops mid-flight on flaky wifi.** Saw `Could not connect to the endpoint URL` on `partNumber=1` after 30 MB of 65 MB had successfully transferred. Fix: always pass `--cli-read-timeout 0 --cli-connect-timeout 60` to make the AWS CLI patient + retry once. Worked first-try on retry with those flags.
- **R2 does NOT implement `GetObjectTagging`.** Server-side copy fails: `aws s3 cp s3://alpacapps/foo s3://alpacapps/bar` → `(NotImplemented) when calling the GetObjectTagging operation`. Workaround: re-upload the local file to the destination key (cheap — same network round-trip).
- **Working manual recipe (verified 2026-05-20, used for v34 + v35):**
  ```bash
  AWS_ACCESS_KEY_ID=$(bw-read "Cloudflare R2 — Object Storage" "Access Key ID") \
  AWS_SECRET_ACCESS_KEY=$(bw-read "Cloudflare R2 — Object Storage" "Secret Access Key") \
  aws s3 cp app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
    s3://alpacapps/langbang/langbang-v${N}-arm64.apk \
    --endpoint-url https://9cd3a280a54ce2a5b382602f0247b577.r2.cloudflarestorage.com \
    --region auto \
    --content-type application/vnd.android.package-archive \
    --cli-read-timeout 0 --cli-connect-timeout 60 \
    --no-progress
  # Then re-upload the same local file to s3://alpacapps/langbang/langbang-latest.apk
  ```

