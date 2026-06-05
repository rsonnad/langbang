# Even Realities G2 Integration Plan

## SDK Shape

Even Hub apps are web apps, not native Android extensions. The phone runs the Even Realities app, hosts the plugin in a WebView, and relays SDK calls to the glasses over Bluetooth. The glasses render SDK containers and emit input events; app logic stays in the phone WebView.

The useful SDK surface for LangBang is small:

- `waitForEvenAppBridge()` to connect to the WebView bridge.
- `createStartUpPageContainer(...)` to create the required first glasses page.
- `TextContainerProperty` for plain text display.
- `textContainerUpgrade(...)` for flicker-free phrase updates.
- `onEvenHubEvent(...)` for press, double press, and scroll events.

Important constraints:

- Display is 576 x 288 pixels per eye.
- Text containers are plain text only. No CSS, font choice, bold, alignment, background fill, or animation.
- One container per page must have `isEventCapture: 1`.
- Full-screen text effectively fits about 400 to 500 characters.
- Unicode is supported only if the firmware font contains the glyph. The first test renders real Polish diacritics by default and has an ASCII fallback toggle for hardware where unsupported glyphs are skipped.
- Packaged apps use `app.json`; `supported_languages` currently does not list `pl`, so the test declares `en` while still displaying Polish content.

## Built-In Teleprompt

The public SDK does not currently expose a Teleprompt import API. Even's consumer Teleprompt feature accepts scripts through the Even Realities app UI, but the SDK package surface only exposes generic display containers, input events, audio, device info, and local storage.

For LangBang, the practical path is to build a small LangBang-owned teleprompter-style plugin instead of trying to write into the native Teleprompt tool:

- Use a text container for the current phrase or script chunk.
- Use `textContainerUpgrade(...)` for live phrase updates.
- Use press/swipe events for previous/next/toggle-gloss controls.
- Later, use the G2 microphone stream if we want speech-following behavior.

Treat any native Teleprompt automation as unsupported unless Even documents a specific import method or deep link.

## First Test

`even-g2-test/` is a standalone Even Hub plugin that can run without changing the Android app.

The test displays LangBang-style phrase cards:

- Polish line
- English translation
- literal gloss
- lesson position

Glasses controls:

- Press: next phrase
- Swipe down: next phrase
- Swipe up: previous phrase
- Double press: hide or show the English/gloss lines

Verification path:

```bash
cd /Users/rahulio/Documents/CodingProjects/LangBangML/even-g2-test
npm install
npm run build
npm run pack
npm run dev
```

Then either run the simulator:

```bash
npm run sim
```

Or sideload to hardware:

```bash
npx evenhub qr --url "http://<mac-lan-ip>:5173"
```

There is no glasses token in the local sideload path. The G2 pairs to the Even Realities app over Bluetooth, the Even app opens this plugin in a WebView, and the SDK bridge is injected there. Even Hub account auth is separate and is only needed for developer portal operations such as private builds or distribution.

## Real LangBang Bridge

The clean integration point in LangBang is `NowVoicingBus`. Every playback path should publish the phrase currently being spoken there, so a glasses feed should mirror that state rather than reimplementing lesson-specific behavior.

Recommended live path:

1. Android publishes the current `NowVoicing` payload to a tiny Supabase Edge Function.
2. The Edge Function stores the latest phrase by device/user and returns CORS-enabled JSON.
3. The Even Hub plugin polls or subscribes to that endpoint and calls `textContainerUpgrade(...)`.

This live feed needs LangBang-owned auth, for example a short bearer token or per-device secret on the Edge Function. That token protects LangBang phrase state; it is not an Even glasses pairing token.

Why this path:

- The G2 plugin runs in the Even app WebView, likely on the phone, while LangBang currently runs on the tablet.
- Direct BLE access is not exposed by the Even SDK.
- Supabase is already shared LangBang infrastructure.
- A server bridge avoids putting R2/Supabase service secrets on either device.

The plugin already supports the expected feed shape through `VITE_LANGBANG_FEED_URL`:

```json
{
  "pl": "Proszę mówić wolniej.",
  "en": "Please speak more slowly.",
  "literal": "Please to-speak more-slowly.",
  "position": "Lesson 5 / language"
}
```
