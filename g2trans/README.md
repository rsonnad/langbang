# LangBangTrans Android

Native Android APK for the direct G2 translation bridge.

## Connection Topology

```text
Even G2 glasses <--BLE--> LangBangTrans Android APK <--WebSocket--> Gemini Live Translate
                                           |
                                           +--> Android microphone
                                           +--> Bluetooth headphones / Android audio output
```

The glasses connect to the Android device running this APK. They do not connect
to Google or LangBang directly.

## Gemini 3.5 Live Translate

The default model is:

```text
gemini-3.5-live-translate-preview
```

The default response mode is `AUDIO`, because Google's Live Translate flow uses
translated audio plus `outputAudioTranscription`. That gives us both:

- English text for the G2 HUD.
- English audio for headphones.

## Configure

Put runtime values in root `local.properties`. The module first reads
`LANGBANGTRANS_*` keys, then falls back to unprefixed keys where useful.

```properties
LANGBANGTRANS_GEMINI_API_KEY=...
LANGBANGTRANS_GEMINI_MODEL=gemini-3.5-live-translate-preview
LANGBANGTRANS_RESPONSE_MODALITIES=AUDIO
LANGBANGTRANS_PLAY_OUTPUT_AUDIO=true

LANGBANGTRANS_G2_DRY_RUN=true
LANGBANGTRANS_G2_SERVICE_UUID=
LANGBANGTRANS_G2_WRITE_CHAR_UUID=
LANGBANGTRANS_G2_NOTIFY_CHAR_UUID=
```

Keep `LANGBANGTRANS_G2_DRY_RUN=true` until the proprietary G2 BLE profile is
confirmed. Real glasses mode requires the G2 service UUID and write/notify
characteristics.

## Build

```bash
./gradlew :g2trans:assembleDebug
```

APK:

```text
g2trans/build/outputs/apk/debug/g2trans-debug.apk
```

## Install

```bash
adb install -r g2trans/build/outputs/apk/debug/g2trans-debug.apk
```

Open `LangBangTrans`, grant microphone/Bluetooth permissions, then press
`Start bridge`.

