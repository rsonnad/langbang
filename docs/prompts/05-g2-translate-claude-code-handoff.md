# TTRAN: G2 Translate Bridge Next Session

Target session: Claude Code
Repo: `/Users/rahulio/Documents/CodingProjects/LangBangML`
Date: 2026-06-14

## Objective

Continue the native Android app `g2trans` until it can:

1. Listen to Polish speech around Rahul through the Android phone microphone.
2. Stream that audio to Gemini Live Translate.
3. Play the English translation audio through the phone's connected headphones.
4. Render the Polish transcript and English transcript on Even Realities G2 glasses.

The glasses connect to the Android phone over BLE. The headphones connect to the Android phone as normal Bluetooth audio. The phone connects to Gemini over the internet.

## Important Boundaries

- Use this checkout: `/Users/rahulio/Documents/CodingProjects/LangBangML`.
- Do not switch to the old bundled LangBang repo.
- Do not revert unrelated dirty worktree files. There are many unrelated LangBang app changes in the same checkout.
- Do not embed a Gemini API key into the public APK. `g2trans/build.gradle.kts` was fixed so `GEMINI_API_KEY` only comes from `LANGBANGTRANS_GEMINI_API_KEY`; public builds currently have an empty key.
- Keep APKs live on the builds page when done.
- For Pixel screenshots, run `instant-screenshot pixel` immediately.

## Current Live APK State

Live builds page:

```text
https://langbang.org/builds
```

Current G2 APK manifest:

```json
{
  "instanceId": "com.sponic.langbangtrans",
  "displayName": "LangBangTrans G2 Translate",
  "direction": "g2trans",
  "versionCode": 3,
  "versionName": "0.1.2",
  "url": "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/g2trans/langbangtrans-latest.apk",
  "pinnedUrl": "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev/langbang/builds/g2trans/langbangtrans-v3.apk",
  "sizeBytes": 4991662
}
```

The Pixel 10 has this installed and visible as:

```text
G2-glas + Gem 3.5 LiveTrans  LBG2 v. 0.1.2
```

Relevant verification screenshot:

```text
/Users/rahulio/Documents/Screenshotz/pixel-20260614-193526-55112.png
```

## What Already Works

Native Android module:

```text
g2trans/
```

Key files:

```text
g2trans/build.gradle.kts
g2trans/version.properties
g2trans/src/main/kotlin/com/sponic/langbangtrans/MainActivity.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/BridgeService.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/BridgeRunner.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/BridgeConfig.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/g2/G2DiscoveryClient.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/g2/G2ConnectionProbeClient.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/g2/G2BleClient.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/g2/G2Protocol.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/gemini/GeminiLiveClient.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/audio/PcmAudio.kt
g2trans/src/main/kotlin/com/sponic/langbangtrans/hud/TextLayout.kt
scripts/publish-langbangml-builds.sh
cloudflare/langbang-org/worker.template.js
```

Current buttons:

```text
START BRIDGE
STOP BRIDGE
DISCOVER G2 BLE PROFILE
TEST G2 BLE CONNECT
```

Versioning:

- `g2trans/version.properties` is the G2 app version source.
- `scripts/publish-langbangml-builds.sh --only=g2trans` bumps the G2 patch version and Android versionCode before building and publishing.
- Current source value after publish:

```text
versionName=0.1.2
versionCode=3
```

Build/publish commands:

```bash
scripts/check-worktree-integrity.sh --allow-current-dirty
LANGBANGML_SKIP_WORKTREE_AUDIT=1 scripts/publish-langbangml-builds.sh --only=g2trans
```

Local build/lint:

```bash
./gradlew :g2trans:assembleDebug :g2trans:lintDebug
```

Install on Pixel:

```bash
adb -s 57091FDCQ0081F install -r g2trans/build/outputs/apk/debug/g2trans-debug.apk
adb -s 57091FDCQ0081F shell am force-stop com.sponic.langbangtrans
adb -s 57091FDCQ0081F shell am start -n com.sponic.langbangtrans/.MainActivity
```

## G2 BLE Findings

The app has successfully discovered and connected to both lenses.

Local discovery report:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML/artifacts/g2-discovery/g2-discovery-20260614-192211.txt
```

Local connect probe report:

```text
/Users/rahulio/Documents/CodingProjects/LangBangML/artifacts/g2-connect-probe/g2-connect-probe-20260614-193838.txt
```

Verified devices:

```text
Even G2_32_L_1CB8F3  F4:16:66:1C:B8:F3
Even G2_32_R_3294E8  F0:08:E2:32:94:E8
```

Both are already bonded:

```text
bondState=12
type=2
```

Command service discovered on both lenses:

```text
service=00002760-08c2-11e1-9073-0e8ac72e5450
write=00002760-08c2-11e1-9073-0e8ac72e5401 properties=write-no-response
notify=00002760-08c2-11e1-9073-0e8ac72e5402 properties=notify
```

Audio-related notify discovered:

```text
service=00002760-08c2-11e1-9073-0e8ac72e6450
audioNotify=00002760-08c2-11e1-9073-0e8ac72e6402 properties=notify
```

`TEST G2 BLE CONNECT` verified:

```text
writeFound=true
notifyFound=true
notifyEnable=local=true descriptor-write-started
```

for both left and right lenses.

## Immediate Next Engineering Step

Add a new button:

```text
SEND TEST TEXT TO GLASSES
```

Goal of that button:

1. Scan for both lenses.
2. Connect both lenses.
3. Enable command notifications.
4. Run the authenticated G2 command startup sequence.
5. Send a minimal EvenHub text page containing:

```text
LangBang G2 test
Polish: czesc
English: hello
```

6. Confirm visible text on the G2 HUD.

Do not start by changing Gemini. The next validation milestone is one static text frame on the glasses.

## Protocol Source To Port

Reference implementation:

```text
https://raw.githubusercontent.com/Mentra-Community/MentraOS/dev/mobile/modules/bluetooth-sdk/android/src/main/java/com/mentra/bluetoothsdk/sgcs/G2.kt
```

Relevant sections in that file:

```text
G2BLE constants: lines around 41-50
ServiceID enum: lines around 54-66
EvenHubCmd enum: lines around 72-80
DevCfgCommandId enum: lines around 128-135
CRC function: lines around 139-149
ProtobufWriter: lines around 153-190
EvenHubProto text/page builders: lines around 277-490
DevSettingsProto auth/time builders: lines around 540-590
EvenBLETransport.buildPackets: lines around 1029-1088
G2SendManager: lines around 1100-1120
Auth sequence: lines around 1588-1642
Text container/send flow: lines around 1840-1945 and 2350-2432
```

The protocol is not the old provisional `G2Packetizer` in `G2Protocol.kt`. The current provisional packetizer uses a made-up layout header/CRC approach and should be replaced or bypassed for real G2 HUD writes.

Known packet frame from Mentra:

```text
byte 0: 0xAA
byte 1: (DEST_GLASSES << 4) | SOURCE_PHONE = 0x21
byte 2: syncId
byte 3: payloadLen, includes CRC bytes on final packet
byte 4: totalPackets
byte 5: serialNum, 1-based
byte 6: serviceId
byte 7: status/reserve flag, bit 5 = reserveFlag
bytes 8..: payload chunk
final packet suffix: CRC16(payload), little-endian low byte then high byte
```

Known constants:

```text
HEADER_BYTE=0xAA
SOURCE_PHONE=1
DEST_GLASSES=2
MAX_PACKET_PAYLOAD=236
DEVICE_SETTINGS service=0x80
EVEN_HUB service=0xE0
ONBOARDING service=0x10
GESTURE_CTRL service=0x0D
G2_SETTING service=0x09
```

Minimum auth/startup sequence from Mentra:

```text
auth left: DevSettingsProto.authCmd(magic)
auth right: DevSettingsProto.authCmd(magic)
pipe role change right: DevSettingsProto.pipeRoleChange(magic)
time sync both: DevSettingsProto.timeSync(magic)
optionally skip onboarding and gesture init
```

Default text container from Mentra:

```text
x=0
y=0
width=576
height=288
borderWidth=0
borderColor=0
borderRadius=0
paddingLength=4
container id pool=1..6
```

EvenHub text flow:

```text
EvenHubProto.textContainerProperty(...)
EvenHubProto.createPageMessage(...)
send service 0xE0
Then for updates:
EvenHubProto.updateTextMessage(containerID=1, contentOffset=0, contentLength=utf8Length, content=text)
send service 0xE0
```

## Gemini Status

`GeminiLiveClient.kt`, `PcmAudio.kt`, and `BridgeRunner.kt` already scaffold the mic -> Gemini -> app audio/text loop, but public builds intentionally do not include an API key.

Next safe options:

1. Add a runtime settings screen/key field for private testing, or
2. Build a backend proxy/token endpoint so the APK never ships a Gemini key.

Do not embed the shared root `GEMINI_API_KEY` in the public APK.

## Acceptance Path For Claude Code

First acceptance target:

```text
Static test text appears on G2 HUD from the Android APK.
```

Second acceptance target:

```text
TextLayoutManager output appears on G2 HUD when START BRIDGE receives transcript updates.
```

Third acceptance target:

```text
Polish ambient speech -> Gemini -> English audio in headphones + Polish/English text on glasses.
```

## Device Test Notes

Keep the glasses awake/on face during BLE tests. Close the official Even app before testing because it may hold BLE connections.

Pixel serial:

```text
57091FDCQ0081F
```

Useful commands:

```bash
adb devices -l
adb -s 57091FDCQ0081F shell am start -n com.sponic.langbangtrans/.MainActivity
adb -s 57091FDCQ0081F shell uiautomator dump /sdcard/window.xml
adb -s 57091FDCQ0081F shell cat /sdcard/window.xml
instant-screenshot pixel
```

When using the current UI, `TEST G2 BLE CONNECT` produced a report at:

```text
/sdcard/Android/data/com.sponic.langbangtrans/files/g2-connect-probe/g2-connect-probe-20260614-193838.txt
```

## Summary

We are not waiting on discovery anymore. BLE discovery and command-channel connect are proven. The next session should implement the authenticated G2 packet/protobuf/EvenHub text sender and validate one static HUD text frame before wiring live Gemini transcripts into it.
