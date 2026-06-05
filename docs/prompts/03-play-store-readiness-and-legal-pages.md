# Prompt 3: Play Store Readiness And Legal Pages

You are working in the LangBangML fork:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML
```

Goal: make LangBangML suitable for Google Play review by creating a Play-safe
Android release path, removing internal/sideload-only behavior from store
artifacts, adding live legal pages to `https://langbang.org`, and documenting
the exact Play Console disclosures that match the shipped app.

Do not ask the user to do steps you can do. Do not publish LangBangML builds,
content, audio, backend changes, or `langbang.org` website changes through the
legacy bundled app repo. The active `langbang.org` Worker source and deploy path
live in this LangBangML checkout.

## Current Context

LangBangML is the Cloudflare-backed LangBang fork. Treat this checkout as the
source of truth for new LangBang work.

Current Cloudflare endpoints:

```text
Worker: https://langbangml-api.langbangml.workers.dev
Public R2 base: https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev
Main R2 prefix: langbang/
```

Current app flavors:

```text
enPl package: com.sponic.langbangml.enpl
enPl instance: langbangml-en-pl
plEn package: com.sponic.langbangml.plen
plEn instance: langbangml-pl-en
```

The existing publisher is debug/R2 oriented:

```bash
scripts/publish-langbangml-builds.sh
```

Do not use the disabled legacy `scripts/publish-r2.sh` in this fork.

## Store Readiness Findings To Address

The current debug/internal build is not Play-ready. Address at least these
findings before any Play submission:

1. Remove APK self-update behavior from Play artifacts.
   - `app/src/main/AndroidManifest.xml` declares `REQUEST_INSTALL_PACKAGES`.
   - `app/src/main/kotlin/com/sponic/langbang/domain/UpdateChecker.kt`
     downloads APKs from R2 and launches Android package install flows.
   - `app/src/main/kotlin/com/sponic/langbang/ui/UpdateBanner.kt` exposes the
     sideload update UI.
   - Play builds should update through Google Play, not through R2 APK
     downloads.

2. Remove internal device-management behavior from Play artifacts.
   - `app/src/main/AndroidManifest.xml` declares `WRITE_SECURE_SETTINGS`.
   - `app/src/main/kotlin/com/sponic/langbang/AdbWifiKeeper.kt` modifies
     wireless debugging and VPN-related secure settings when granted.
   - Any boot/tablet-management behavior that exists only for the controlled
     Samsung tablet workflow must be absent from Play artifacts.

3. Remove client-shipped provider secrets from Play artifacts.
   - `app/build.gradle.kts` injects `AZURE_SPEECH_KEY`,
     `AZURE_SPEECH_REGION`, and `GEMINI_API_KEY` into `BuildConfig`.
   - `AzureTtsClient` and `AzurePronunciationClient` can use the Azure key
     directly from the APK.
   - For Play, route Azure/Gemini calls through the Worker or remove the
     direct fallback entirely. Do not ship usable provider keys in the APK.

4. Add backend abuse controls before broad distribution.
   - Public Worker routes can generate provider cost:
     `/v1/gemini/generate`, `/v1/phrases/complete`, and
     `/v1/audio/manifest`.
   - Add appropriate app auth, quotas, rate limits, Cloudflare WAF/rate-limit
     rules, or another defensible gating mechanism.
   - Keep admin routes and app-user routes separate.

5. Align privacy, Data safety, account, and analytics behavior.
   - Worker migrations include auth, user phrase sync, and analytics tables.
   - The Android client may not use every backend feature yet, but the live
     service must still have retention, deletion, admin access, and abuse
     rules before launch.
   - If a Play build includes sign-in or user phrase sync, add in-app account
     deletion or a clear web/email deletion path that satisfies Play Console
     requirements.

6. Fix first-run permission and disclosure flow.
   - `MainActivity` currently requests microphone permission on launch.
   - Request microphone permission only when the user enters pronunciation or
     another mic-backed feature.
   - Add in-app links to Privacy Policy and Terms.

7. Create a real Play release pipeline.
   - Generate signed release AABs, not debug APKs, for any Play submission.
   - Use Play App Signing and a proper upload key.
   - Decide whether `enPl` and `plEn` are two separate Play listings or one app
     with an in-app direction switch. If separate, each package needs its own
     listing, Data safety form, privacy URL, screenshots, and review answers.
   - Stop mutating `version.properties` as an incidental side effect of
     ordinary assemble/install tasks. Release version increments should be
     explicit and reproducible.

8. Close technical quality gaps.
   - Run lint for every Play artifact and resolve policy-sensitive warnings:
     fixed orientation, ChromeOS ABI support, Android ID use, backup rules,
     launcher icon monochrome assets, and stale dependencies.
   - Add focused unit/UI/backend-contract tests for flavor config, Play-build
     exclusions, Worker response parsing, privacy/legal links, and
     pronunciation permission flow.

## Legal Pages On langbang.org

Create and deploy real pages at:

```text
https://langbang.org/privacy
https://langbang.org/terms
```

Also add visible links in the site nav/footer and in the Android app settings
or about surface.

The current live behavior must not remain that `/privacy` and `/terms` return
the home page. Verify the body content after deploy.

### Privacy Policy Must Cover

Include a clear effective date and contact email. Cover all app packages and
flavors:

```text
LangBang EN-PL: com.sponic.langbangml.enpl
LangBang PL-EN: com.sponic.langbangml.plen
```

Disclose at minimum:

- Microphone audio used for pronunciation assessment and any transmission to
  Microsoft Azure Speech services.
- Text entered by the user, generated phrase prompts, lesson/custom phrase
  data, and language-learning content sent through the LangBangML Worker and
  Google Gemini where applicable.
- Audio downloads, audio prefetching, and backend requests on launch.
- Optional user-configured SFTP backup content, including app data,
  preferences, cached audio, and device metadata if retained.
- Authentication data if sign-in is enabled: email, Google profile fields,
  sessions, email login codes, user phrase sync, and deletion process.
- Product analytics if enabled: profile IDs, installation IDs, session IDs,
  app version, package/flavor, device model, OS version, locale, event names,
  screen/feature names, durations, and properties payloads.
- Cloudflare logs, IP addresses, request metadata, abuse prevention, and
  operational diagnostics.
- Third-party processors: Cloudflare, Microsoft Azure, Google Gemini/Google
  identity if used, and any email provider used for login codes.
- Retention periods or deletion criteria for user accounts, analytics, auth
  sessions, backups, logs, and generated/cache data.
- Data deletion request path.
- No ads and no sale of personal data, unless that changes.
- Target audience. If the app is not directed to children, say that clearly.

The policy must match the Play Console Data safety form. If a field is present
in any Play-distributed version of a package, disclose it for that package.

### Terms Must Cover

Include at minimum:

- Educational/language-learning purpose.
- AI-generated or machine-generated content may be inaccurate.
- Pronunciation scoring is educational feedback, not a guarantee of fluency.
- User responsibility for custom phrases and submitted content.
- Acceptable use and no abuse of backend generation/audio services.
- No reverse engineering, scraping, credential extraction, or service abuse.
- Third-party services and privacy policy reference.
- Service availability, changes, and updates through Google Play.
- Disclaimers and liability limits appropriate for a small educational app.
- Contact path for support, privacy, account deletion, and content issues.

## Play Console Preparation

Prepare a document or checklist in the repo for the exact Play Console answers.
Include:

- App names, package names, app category, and short/long descriptions.
- Target audience and content rating inputs.
- Data safety answers by package/flavor.
- Permissions declaration answers, especially microphone. A Play artifact
  should not need `REQUEST_INSTALL_PACKAGES` or `WRITE_SECURE_SETTINGS`.
- Privacy policy URL.
- Terms URL.
- AI-generated content policy assessment and reporting/contact path.
- Login credentials or reviewer instructions if sign-in gates any feature.
- Closed testing plan if the developer account is a newer personal Play
  account and requires testing before production access.
- Screenshot and store asset requirements for phones/tablets.

Useful official references:

```text
Target SDK: https://developer.android.com/google/play/requirements/target-sdk
Android App Bundle requirement: https://support.google.com/googleplay/android-developer/answer/9844679
REQUEST_INSTALL_PACKAGES policy: https://support.google.com/googleplay/android-developer/answer/12085295
Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
AI-generated content: https://support.google.com/googleplay/android-developer/answer/14094294
New personal account testing: https://support.google.com/googleplay/android-developer/answer/14151465
```

## Suggested Implementation Shape

Prefer a narrow, reviewable sequence:

1. Add a Play-safe build variant or flavor dimension.
2. Exclude sideload updates, debug secure-settings helpers, debug-only
   receivers, and client provider keys from Play artifacts.
3. Add release signing/AAB generation without changing the existing R2 debug
   publisher used for tablet iteration.
4. Add privacy and terms source files in this repo and deploy them to
   `langbang.org`.
5. Add Android in-app legal links and contextual microphone permission flow.
6. Add backend abuse controls and deletion/retention handling for any
   user/auth/analytics features that are live or exposed.
7. Add tests and lint gates.
8. Produce a Play Console readiness checklist with exact answers.

Keep debug/internal workflows working for the Samsung tablet and R2 publisher,
but make it impossible to accidentally submit those internal capabilities to
Google Play.

## Verification Gate

Before reporting complete, verify all of the following:

```bash
./gradlew --no-configuration-cache :app:lintEnPlDebug :app:lintPlEnDebug
```

Also run the new Play lint/build tasks that you create, for example:

```bash
./gradlew --no-configuration-cache :app:lintEnPlRelease :app:lintPlEnRelease
./gradlew --no-configuration-cache :app:bundleEnPlRelease :app:bundlePlEnRelease
```

Adjust task names to the final build variant names.

Inspect the generated Play AAB/APK manifest and confirm it does not contain:

```text
android.permission.REQUEST_INSTALL_PACKAGES
android.permission.WRITE_SECURE_SETTINGS
client-side Azure/Gemini secret values
R2 APK self-update UI or installer flows
```

Verify live website:

```bash
curl -fsS https://langbang.org/privacy
curl -fsS https://langbang.org/terms
```

Confirm the response bodies contain actual Privacy Policy and Terms content,
not the home page.

Verify backend and public build state without confusing source, tablet-installed,
R2-debug-published, and Play-candidate states:

```bash
curl -fsS https://langbangml-api.langbangml.workers.dev/health
curl -fsS https://langbangml-api.langbangml.workers.dev/v1/instances
curl -fsS https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/en-pl/latest.json
curl -fsS https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/pl-en/latest.json
```

Final answer should state:

```text
what was changed
which Play artifacts were built
which permissions/secrets were verified absent
privacy and terms live URLs
lint/test results
remaining Play Console manual tasks, if any
source vs tablet-installed vs R2-debug-published vs Play-candidate state
```
