# Play Console — Data Safety, Permissions & Content Rating answers

Exact answers to enter in the Play Console, derived from the 2026-06-29 audit of
the shipped app + backend. Keep this in sync with `langbang.org/privacy`. Applies
to `com.sponic.langbangml.enpl` (and `.plen` when it launches).

> Re-confirm against the **Play-safe release build** before submitting — once the
> Play variant strips the SFTP backup / debug features (plan AND-1..3), drop the
> corresponding rows.

## Data collection & sharing

"Shared" = sent to a third party. All transfers are encrypted in transit
(HTTPS/WSS). Users can request deletion (see Account deletion below).

| Data type | Collected | Shared | Processed by | Purpose | Optional? |
|---|---|---|---|---|---|
| Email address | Yes (if signed in) | No¹ | Cloudflare (our backend), Google (Sign-In) | Account, sync | Optional (sign-in) |
| Name | Yes (Google sign-in) | No¹ | Cloudflare, Google | Account | Optional |
| Photos (profile picture URL) | Yes (Google sign-in) | No¹ | Cloudflare, Google | Account display | Optional |
| Voice / audio (microphone) | Yes, ephemeral | **Yes** | Microsoft Azure Speech | Pronunciation scoring | Optional (feature-gated) |
| User content (custom phrases/words) | Yes (if signed in) | **Yes** | Cloudflare, Google Gemini | Generate/validate/sync content | Optional |
| App interactions / analytics | Yes | No¹ | Cloudflare (first-party) | Analytics, app function | Collected |
| Device or other IDs (random installation ID) | Yes | No¹ | Cloudflare | Analytics, abuse prevention | Collected |
| Diagnostics (app version, OS, device model, locale) | Yes | No¹ | Cloudflare | Analytics, diagnostics | Collected |
| IP address | Yes (server logs) | No¹ | Cloudflare | Security, abuse prevention | Collected |

¹ "No" in Play's sense = not shared for advertising/analytics with third parties;
processors that operate the service on our behalf (Cloudflare, Azure, Google,
Resend) are disclosed in the privacy policy. Audio + user content ARE marked
**Shared** because they are sent to Azure / Google to provide the feature.

**Not collected:** location, contacts, calendar, SMS, financial info, health,
browsing history, ads identifiers. **No ads. No sale of data.**

## Security & deletion (Data safety form)

- Data encrypted in transit: **Yes** (all HTTPS/WSS).
- Users can request data deletion: **Yes** — in-app account settings (when
  shipped) and via email to `langbangapp@gmail.com`. Backend endpoint:
  `DELETE /v1/me` (or `POST /v1/me/delete`) purges account, identities, sessions,
  and all user-owned content, and anonymizes the analytics profile.
- Account creation: optional (app is usable signed-out).

## Permissions declaration

| Permission | Why | Play notes |
|---|---|---|
| `RECORD_AUDIO` | Pronunciation assessment | Requested contextually (not at launch — plan AND-5) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Backend + audio | Normal |
| `RECEIVE_BOOT_COMPLETED` | (debug-only helper) | **Must be absent from Play build** (plan AND-1..3) |
| `WRITE_SECURE_SETTINGS` | (debug ADB-WiFi helper) | **Must be absent from Play build** — would be rejected |
| `REQUEST_INSTALL_PACKAGES` | (sideload self-update) | **Must be absent from Play build** — use Play updates |

The Play artifact must contain **none** of the bottom three. Verify by unzipping
the AAB and checking the merged manifest (plan AND-10).

## Content rating questionnaire

- Category: **Education**.
- Violence / sexual / profanity / controlled substances / gambling: **None**.
- User-generated content: **Yes** — users create custom phrases (private to their
  account; not shared between users today). AI-assisted; see AI disclosure.
- Expected rating: **Everyone**.

## AI-generated content disclosure (Play AI policy)

- The app uses AI (Google Gemini) to generate, complete, and validate
  language-learning phrases on user request.
- Disclosed in-app and in the listing; output may be imperfect and users are told
  to review it.
- Reporting/contact channel for problematic AI output: `langbangapp@gmail.com`.
- Keep listing AI/API copy gated per `play-store-listing-copy.md` until the agent
  API path ships in the Play-target release.

## Reviewer access

If sign-in gates any reviewed feature, provide test credentials via the backend
`/v1/auth/test-login` (enable by setting `TEST_LOGIN_EMAIL` + `TEST_LOGIN_PASSWORD`
worker secrets for review, then unset after).
