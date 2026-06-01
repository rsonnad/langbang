# Even Hub Pilot Program — Application Draft (LangBang G2)

> Paste-ready answers for the Even Hub developer pilot. Edit the **[BRACKETED]**
> bits (they're personal — availability, name, contact). Everything else is
> grounded in the real plugin in `even-g2-test/`.

## Where to submit
- **Console / sign-in:** https://hub.evenrealities.com/hub — sign in with the
  email on your Even Realities account (the SAME account paired to the G2 in the
  phone app). This is the modern entry point; the old `/application` form now
  routes here.
- **Discord (community + access inquiries):** https://discord.gg/GsuDkKDXDe
- **Critical:** apply with, log into the Console with, and run `evenhub login`
  with the *same* email that's signed into the Even Realities app on the device.
  Approval flips the Developer Center / QR scanner on for that one account.

---

## 1. About you / background
I build small, focused apps end to end — design, native code, and backend. My
current project is **LangBang**, a Polish-learning app for Android tablets
(Kotlin + Jetpack Compose, Supabase backend, Cloudflare R2 delivery, Azure
neural TTS). I own an Even Realities G2 and use it daily, and I already have a
working Even Hub plugin running in the simulator. I'm comfortable across the
stack the pilot needs: TypeScript web plugins, the Even Hub SDK, CLI packaging
(`.ehpk`), and the server side that feeds live content to a plugin.

## 2. Project idea (the main pitch)
**LangBang G2 — a hands-free "now voicing" heads-up teleprompter for language
learners.**

The phrase you're currently practicing in LangBang appears on the G2 in real
time, mirrored from the app's playback bus, so you can shadow-speak a language
with your eyes up — walking, commuting, doing dishes — instead of staring at a
tablet. Each card shows the Polish line, the English translation, a literal
word-for-word gloss, and your lesson position.

**This already exists as a prototype.** `even-g2-test/` is a standalone Even Hub
plugin (package `com.sponic.langbangg2`) that:
- Connects via `waitForEvenAppBridge()` and creates the startup page with
  `createStartUpPageContainer(...)`.
- Renders LangBang phrase cards in a `TextContainerProperty` and updates them
  flicker-free with `textContainerUpgrade(...)`.
- Handles G2 input through `onEvenHubEvent(...)`: **press / swipe-down** = next
  phrase, **swipe-up** = previous, **double-press** = toggle the English/gloss
  lines.
- Renders real Polish diacritics with an ASCII fallback for firmware fonts that
  skip glyphs, inside the 576×288 per-eye text constraints.
- Is verified in `@evenrealities/evenhub-simulator` (non-blank framebuffer with
  Polish text) and packs cleanly to a `.ehpk`.

**What the pilot unlocks:** the live feed. LangBang already has a
`NowVoicingBus` that every playback path publishes to. The plan is: the app
pushes the current phrase to a tiny Supabase Edge Function (CORS JSON, secured
with a per-device LangBang token — not an Even pairing token), and the plugin
polls/subscribes and calls `textContainerUpgrade(...)`. The plugin already reads
this exact feed shape via `VITE_LANGBANG_FEED_URL`:
```json
{ "pl": "Proszę mówić wolniej.", "en": "Please speak more slowly.",
  "literal": "Please to-speak more-slowly.", "position": "Lesson 5 / language" }
```
I need real-hardware time to validate glyph coverage, event ergonomics, update
latency, and battery — things the simulator can't tell me.

**Why it fits the G2:** glanceable, low-text, audio-paired, hands-free. It's the
language-learning analogue of Teleprompt, but driven by an app's live "what am I
hearing right now" state rather than a pasted script.

## 3. Availability
[ADJUST] I can start immediately. Roughly **[6–10] hours/week**, mostly
**[evenings and weekends]**, [TIMEZONE: e.g. America/Los_Angeles]. I already have
the dev environment, simulator, and a packaged build ready, so first on-device
testing can happen the same day access lands. Happy to give regular feedback in
the pilot Discord.

## 4. Portfolio / links
- **Plugin + app source:** https://github.com/rsonnad/langbang
- **LangBang landing page:** https://alpacaplayhouse.com/rahulio/pages/langbang/
- **Latest build (R2):** https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/langbang-latest.apk
- [OPTIONAL: add a 20-sec screen capture of the plugin running in the simulator —
  the framebuffer screenshot at `captures/langbang-g2-glasses-current-visible.png`
  is a good still if you want one image.]

## 5. Device (if asked)
Yes — I own a G2, it's paired and connected in the Even Realities app
(`com.even.sg`). I'm developing against the official SDK + simulator today and
just need the Developer Center / QR sideload entitlement to test on the hardware.

---

## Short version (single free-text box / Discord intro)
> I'm building **LangBang G2**, a hands-free "now voicing" teleprompter for
> language learners: the Polish phrase you're currently practicing in my
> LangBang app appears on the G2 in real time (Polish + English + literal gloss
> + lesson position), so you can shadow-speak with eyes up. It's already a
> working Even Hub plugin — `waitForEvenAppBridge` + `textContainerUpgrade`,
> press/swipe/double-press controls, Polish diacritics with ASCII fallback,
> verified in the simulator and packed to `.ehpk`. I own a paired G2 and have a
> live phrase-feed (Supabase Edge Function → `textContainerUpgrade`) ready to
> wire up. I just need on-device access to validate glyph coverage, event
> ergonomics, and update latency. Source: github.com/rsonnad/langbang.
