# Google Auth Account Plan

Current implementation target:

- Temporary Google Cloud account: `alpacaplayhouse@gmail.com`
- Android/Worker config key: `GOOGLE_WEB_CLIENT_ID`
- Worker secret/var expected by auth: `GOOGLE_WEB_CLIENT_ID`

Migration target:

- Move the OAuth client and related Google Auth Platform configuration to a
  dedicated LangBang Google account before a broader public release.
- Keep the Web client ID as configuration only, so migration does not require
  Android or Worker code changes beyond replacing `GOOGLE_WEB_CLIENT_ID`.

Notes:

- The existing Gemini API key is already associated with the temporary
  alpacapps account path.
- Do not store OAuth client secrets in the Android app. The app only needs the
  Web client ID; the Worker verifies Google ID tokens server-side.
- As of 2026-06-04, `gcloud` has `alpacaplayhouse@gmail.com` authenticated but
  no active project selected for that account and no stored LangBang OAuth
  client ID in Bitwarden/local config.
- As of 2026-06-04, the LangBang Google Cloud project has Web client ID
  `385515827732-9r056blngf9vpv4r2ersiiv8lte5trvb.apps.googleusercontent.com`
  saved in Worker vars and local Android build config.
- Create a Google Auth Platform Web client under the alpacapps account, then set
  `GOOGLE_WEB_CLIENT_ID` in both Android local build config and the Worker.
- Initial Android launch scope is EN->PL only. Create the Android OAuth client
  for package `com.sponic.langbangml.enpl` now; defer
  `com.sponic.langbangml.plen` until the PL->EN flavor is ready to launch.
- For the Web client, use application type `Web application`.
- Authorized JavaScript origins:
  - `https://langbang.org`
  - `https://langbangml-api.langbangml.workers.dev`
- Redirect URIs are not required for the admin analytics page because Google
  Identity Services returns the ID token to the browser callback.
- Worker config:
  - add `GOOGLE_WEB_CLIENT_ID="<client-id>.apps.googleusercontent.com"` under
    `cloudflare/langbangml/wrangler.toml` `[vars]`
  - deploy with `npx wrangler deploy`
- Android local build config:
  - add the same `GOOGLE_WEB_CLIENT_ID=...` to `local.properties`
  - rebuild and publish the affected debug flavor(s), starting with `enPl`
- Current fallback login for `https://langbang.org/admin`:
  - copy the password from Bitwarden item
    `Cloudflare — LangBangML Content API — New Account`
  - paste it as the Bearer token on the admin page
- Email login is deployed server-side. Resend is configured through the
  `devops-langbang` Bitwarden item `Resend - LangBang Email API` and live Worker
  secrets `RESEND_API_KEY`/`EMAIL_FROM`.
- The current Resend sender is `LangBang <onboarding@resend.dev>`. Replace it
  with a verified LangBang domain sender before broad public email-code
  delivery.
