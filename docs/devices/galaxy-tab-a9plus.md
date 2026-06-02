# Target Device — Samsung Galaxy Tab A9+ Wi-Fi

Verified from device via ADB on 2026-05-18.

## Identity
- Model: SM-X210 (Galaxy Tab A9+ Wi-Fi)
- Manufacturer: Samsung
- OS: Android 16 (API level 36)
- Samsung security patch: 2026-02-05

## SoC & CPU
- Chipset: Qualcomm Snapdragon 695 5G (model SM6375, codename `blair`)
- Process: 6 nm
- Cores: 8 (octa-core ARMv8.2)
  - 2x Kryo 660 Gold (Cortex-A78) @ 2.2 GHz
  - 6x Kryo 660 Silver (Cortex-A55) @ 1.8 GHz
- ABIs supported: `arm64-v8a` (primary), `armeabi-v7a`, `armeabi`

## GPU
- Adreno 619 @ ~565 MHz
- OpenGL ES 3.2 (driver V@0762.40, Jul 2025)
- Vulkan 1.3 (level=1, compute supported, deqp.level=132580097)
- ~750 in 3DMark Wild Life (mid-range; sufficient for UI/video/light ML)

## Memory & Storage
- RAM: 4 GB total (~3.4 GiB usable)
  → tight; cache audio progressively, do not preload library
- `/data` partition: 48 GB (currently 16 GB free)
- Recommended app footprint budget: <500 MB total including bundled assets

## Display
- Panel: 11" WUXGA+ (1920×1200), 16:10, 90 Hz refresh
- Density: 320 dpi (xhdpi, 2.0× density bucket)
- In dp: 960dp × 600dp landscape → use `sw600dp` qualifier
- Supported refresh rates: 60 Hz, 90 Hz (adaptive)
- HDR: not supported (max luminance 500 nits)
- Color modes: SDR only

## Audio (TTS playback path)
- `android.hardware.audio.low_latency`: TRUE (responsive playback)
- `android.hardware.audio.output`: TRUE
- `android.hardware.microphone`: TRUE (usable for STT pronunciation features)
- TTS engine pre-installed: `com.google.android.tts` (Google TTS)
  → for production Polish, prefer pre-generated MP3/Opus from cloud TTS
    (Azure `pl-PL-AgnieszkaNeural` etc.) over on-device TTS for quality

## Cameras (if OCR/visual input ever needed)
- Rear: full-level Camera2 support, RAW capable, manual sensor+post-processing
- Front: yes
- Autofocus: yes

## Sensors
- Compass, gyroscope, ambient light, proximity, skin-temperature
- Touchscreen: `multitouch.jazzhand` (5+ simultaneous touch points)

## Locale support (IMPORTANT for Polish/English app)
- System locales installed by default: `en-US`, `en-GB` only
- Polish (`pl-PL`) NOT installed as a system locale
  - App MUST ship `res/values-pl/strings.xml` for Polish UI
  - ICU formatting for Polish works regardless (built into Android)
  - User can add Polish via Settings → General management → Language

## Network
- Wi-Fi only (no cellular; SM-X210 = Wi-Fi variant of A9+)
- Tablet is on the Sponic Tailscale tailnet (IP `100.103.110.7`)

## Battery & power profile
- Li-ion, 7,040 mAh (Samsung spec)
- Charges over USB-C (15 W max)
- Aggressive Samsung Adaptive Battery — background work may be throttled.
  For ongoing audio sessions, use a Foreground Service with a media notification.

## Dev workflow context
- Wireless ADB enabled, paired persistently from dev Mac
- ADB host: `100.103.110.7:<rotating port>` — use `~/bin/adb-tab` wrapper to auto-discover
- Manual screenshot source: `/sdcard/DCIM/Screenshots`; pull latest files with
  `scripts/pull-tablet-screenshots.sh` into
  `~/Documents/Screenshotz/TabA9-YYYYMMDD-HHMMSS`
- Persistent connect port at time of capture: `34975`
- Stay-awake-on-charge recommended during dev
- "Verify apps over USB" OFF (developer's own builds, faster install loop)
- "Disable ADB authorization timeout" recommended ON (paired key never expires)

## Design implications (top 4)
1. **4 GB RAM is the real constraint** — design for streaming audio, not preloading the full language library.
2. **Polish isn't a system locale** — package your own Polish strings rather than assuming `Locale.getDefault()` returns Polish.
3. **11" / 960×600 dp landscape** — design at the `sw600dp` tablet breakpoint, not phone-first.
4. **Adreno 619 + Vulkan 1.3** — Jetpack Compose, Lottie, ExoPlayer all run smoothly; on-device TFLite GPU delegate is feasible but modest (don't plan flagship-tier on-device STT).
