# LangBang G2 Test

Small Even Hub plugin for testing Polish phrase display on Even Realities G2 glasses.

The current SDK model is a web plugin running inside the Even Realities phone app WebView. The plugin talks to the glasses through `@evenrealities/even_hub_sdk`; it is not a native Android BLE library.

## What This Tests

- Creates one full-screen text container on the 576 x 288 G2 canvas.
- Displays a LangBang-style Polish phrase, English translation, and literal gloss.
- Uses press / up / down / double press events to cycle phrases and hide or show the gloss.
- Renders native Polish diacritics by default, with a companion-page ASCII fallback toggle if hardware skips glyphs.
- Supports an optional live phrase feed through `VITE_LANGBANG_FEED_URL`.

## Local Run

```bash
npm install
npm run dev
```

In another terminal:

```bash
npm run sim
```

The simulator serves an automation API on `http://127.0.0.1:9898`.

## Hardware Sideload

Run the Vite dev server on the LAN:

```bash
npm run dev
```

Then generate a QR code with the machine's LAN IP:

```bash
npx evenhub qr --url "http://<mac-lan-ip>:5173"
```

Scan the QR code with the Even Realities app.

There is no glasses token for this local path. The glasses pair to the Even Realities app over Bluetooth; this web app connects to the SDK bridge that the Even app injects into its WebView. Even account login is only needed for developer portal workflows such as private builds or distribution.

## Package

```bash
npm run pack
```

This writes `dist/langbang-g2-test.ehpk`.

## Optional Feed Shape

Set `VITE_LANGBANG_FEED_URL` to an endpoint returning:

```json
{
  "pl": "Proszę mówić wolniej.",
  "en": "Please speak more slowly.",
  "literal": "Please to-speak more-slowly.",
  "position": "Lesson 5 / language"
}
```

The plugin polls once per second. For the real LangBang integration, this should be backed by a CORS-enabled Supabase Edge Function that exposes the current `NowVoicing` phrase.
