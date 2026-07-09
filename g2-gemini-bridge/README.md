# G2-Gemini-Bridge / LangBangTrans

Dedicated experimental bridge for translating ambient Polish speech into:

- Polish live transcript on Even Realities G2
- English live translation on Even Realities G2
- Optional translated English audio through the host audio output

This is intentionally separate from `even-g2-test/`. The existing app uses the
Even Hub WebView SDK. This bridge bypasses the official companion app and talks
to the glasses over BLE with `bleak`.

## Current State

Implemented:

- BLE scan, persistent reconnect, notifications, init packet replay, throttled writes.
- Async microphone capture at 16 kHz, 16-bit mono PCM.
- Gemini Live raw WebSocket session setup, audio streaming, transcript parsing, and optional output audio playback.
- G2 HUD text wrapping at 28 columns and 10 lines.
- CRC-16/CCITT packet construction with configurable channel headers.
- Dry-run/demo mode that exercises the HUD layout and packetizer without hardware.

Requires hardware-specific confirmation:

- G2 proprietary service UUID.
- G2 write and notify characteristic UUIDs.
- The real 7-packet init sequence.
- The exact content/rendering channel header bytes.
- Whether the firmware wants the CRC as ASCII hex, binary big-endian, or binary little-endian.

The direct BLE protocol values are not exposed by the public Even Hub SDK. Keep
the bridge in `LANGBANGTRANS_DRY_RUN=1` until those values are captured from a
known-good session or confirmed against firmware traces.

## Why the Output Mode Is Configurable

Google's current Live Translate documentation shows `gemini-3.5-live-translate-preview`
configured with `responseModalities: ["AUDIO"]`, `inputAudioTranscription`, and
`outputAudioTranscription`. The translated text is the transcription of the
translated audio stream. If you force `TEXT` only, that may work only if Google
adds support beyond the documented Live Translate path.

This bridge therefore exposes both:

- `LANGBANGTRANS_RESPONSE_MODALITIES=AUDIO`: documented Live Translate path, supports translated audio playback.
- `LANGBANGTRANS_RESPONSE_MODALITIES=TEXT`: strict text-only experiment, no headphone audio.

## Setup

```bash
cd /Users/rahulio/Documents/CodingProjects/LangBangML/g2-gemini-bridge
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
cp .env.example .env
```

Edit `.env` and set at minimum:

```bash
LANGBANGTRANS_GEMINI_API_KEY=...
LANGBANGTRANS_G2_SERVICE_UUID=...
LANGBANGTRANS_G2_WRITE_CHAR_UUID=...
LANGBANGTRANS_G2_NOTIFY_CHAR_UUID=...
LANGBANGTRANS_G2_INIT_PACKETS_HEX=hexpacket1,hexpacket2,hexpacket3,hexpacket4,hexpacket5,hexpacket6,hexpacket7
LANGBANGTRANS_G2_LAYOUT_HEADER_HEX=...
```

## Run

Dry-run the local rendering and packet construction:

```bash
langbangtrans --demo --duration-seconds 4
```

Scan for candidate glasses:

```bash
langbangtrans --scan
```

Run the bridge:

```bash
langbangtrans
```

Run with translated audio playback:

```bash
LANGBANGTRANS_PLAY_OUTPUT_AUDIO=1 langbangtrans
```

## Test

```bash
PYTHONPATH=src python -m unittest discover -s tests
```

## Packet Model

The packetizer builds one or more BLE write packets for each 10-line HUD frame:

```text
[layout header][fragment index][fragment count][UTF-8 frame fragment][CRC trailer]
```

The CRC is CRC-16/CCITT-FALSE over everything before the trailer:

```text
poly=0x1021, init=0xFFFF, xorout=0x0000
```

`LANGBANGTRANS_G2_CRC_ENCODING` controls the trailer:

- `ascii-hex`: four ASCII bytes like `1A2B`.
- `binary-be`: two bytes, network order.
- `binary-le`: two bytes, little-endian.

The `layout header` is a configurable byte prefix because Even's direct BLE
rendering channel is proprietary. Use one profile per discovered firmware
protocol rather than embedding guessed constants in code.
