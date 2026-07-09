from __future__ import annotations

import argparse
import asyncio
import logging
import signal
from contextlib import suppress
from dataclasses import replace

from .audio import AsyncPcmMicrophone, AudioCaptureConfig, OutputAudioPlayer
from .config import BridgeConfig
from .g2_ble import EvenG2BleClient
from .g2_protocol import G2Packetizer
from .gemini_live import GeminiEvent, GeminiLiveClient
from .layout import HudTextState, TextLayoutManager
from .logging_setup import configure_logging

log = logging.getLogger(__name__)


def cli() -> None:
    parser = argparse.ArgumentParser(description="LangBangTrans G2/Gemini bridge")
    parser.add_argument("--scan", action="store_true", help="scan for candidate G2 BLE devices and exit")
    parser.add_argument("--demo", action="store_true", help="run a local transcript/HUD/packet dry run")
    parser.add_argument("--duration-seconds", type=float, default=6.0, help="demo duration")
    parser.add_argument("--validate-config", action="store_true", help="print redacted config and exit")
    args = parser.parse_args()

    config = BridgeConfig.from_env()
    configure_logging(config.logging)

    if args.validate_config:
        for line in config.redacted_lines():
            print(line)
        problems = config.validate_for_real_run()
        if problems:
            print("problems:")
            for problem in problems:
                print(f"- {problem}")
        return

    if args.scan:
        asyncio.run(scan(config))
        return

    if args.demo:
        asyncio.run(demo(config, args.duration_seconds))
        return

    asyncio.run(run_bridge(config))


async def scan(config: BridgeConfig) -> None:
    candidates = await EvenG2BleClient.scan(config.g2)
    if not candidates:
        print("No candidate G2 devices found.")
        return
    for candidate in candidates:
        services = ", ".join(candidate.service_uuids) or "-"
        print(f"{candidate.address}  {candidate.name}  rssi={candidate.rssi}  services={services}")


async def demo(config: BridgeConfig, duration_seconds: float) -> None:
    demo_g2 = _g2_config_for_packetizer(config)

    packetizer = G2Packetizer(demo_g2)
    g2 = EvenG2BleClient(demo_g2, packetizer)
    layout = TextLayoutManager(config.hud.columns, config.hud.lines)
    state = HudTextState()

    samples = [
        GeminiEvent(input_text="Dzien dobry, czy mozesz mowic wolniej?"),
        GeminiEvent(output_text="Good morning, can you speak more slowly?"),
        GeminiEvent(input_text="Uczę się polskiego i potrzebuję tłumaczenia w okularach."),
        GeminiEvent(output_text="I am learning Polish and need the translation in my glasses."),
    ]

    stop_event = asyncio.Event()
    connection_task = asyncio.create_task(g2.run_connection_loop(stop_event))
    try:
        end_time = asyncio.get_running_loop().time() + duration_seconds
        index = 0
        while asyncio.get_running_loop().time() < end_time:
            event = samples[index % len(samples)]
            state.apply(
                input_text=event.input_text,
                output_text=event.output_text,
                mode=config.hud.transcript_mode,
            )
            frame = layout.render(state)
            print("\n--- HUD frame ---")
            print(frame)
            await g2.send_hud_text(frame)
            index += 1
            await asyncio.sleep(1)
    finally:
        stop_event.set()
        connection_task.cancel()
        with suppress(asyncio.CancelledError):
            await connection_task


async def run_bridge(config: BridgeConfig) -> None:
    problems = config.validate_for_real_run()
    if problems:
        raise SystemExit("Config problems:\n" + "\n".join(f"- {problem}" for problem in problems))

    stop_event = asyncio.Event()
    _install_signal_handlers(stop_event)

    audio_queue: asyncio.Queue[bytes] = asyncio.Queue(maxsize=100)
    event_queue: asyncio.Queue[GeminiEvent] = asyncio.Queue(maxsize=100)
    output_audio_queue: asyncio.Queue[bytes] = asyncio.Queue(maxsize=100)

    g2_config = _g2_config_for_packetizer(config)
    packetizer = G2Packetizer(g2_config)
    g2 = EvenG2BleClient(g2_config, packetizer)
    gemini = GeminiLiveClient(config.gemini)
    microphone = AsyncPcmMicrophone(
        AudioCaptureConfig(
            sample_rate=config.audio.capture_sample_rate,
            frame_ms=config.audio.capture_frame_ms,
            input_device=config.audio.input_device,
        ),
        audio_queue,
    )

    tasks = [
        asyncio.create_task(g2.run_connection_loop(stop_event), name="g2-ble"),
        asyncio.create_task(microphone.run(stop_event), name="audio-capture"),
        asyncio.create_task(
            gemini.run(
                audio_queue,
                event_queue,
                stop_event,
                capture_frame_ms=config.audio.capture_frame_ms,
                sample_rate=config.audio.capture_sample_rate,
            ),
            name="gemini-live",
        ),
        asyncio.create_task(
            hud_render_loop(event_queue, output_audio_queue, g2, config, stop_event),
            name="hud-render",
        ),
    ]

    if config.gemini.play_output_audio:
        player = OutputAudioPlayer(
            config.gemini.output_sample_rate,
            output_audio_queue,
            output_device=config.audio.output_device,
        )
        tasks.append(asyncio.create_task(player.run(stop_event), name="audio-output"))

    try:
        done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_EXCEPTION)
        for task in done:
            exc = task.exception()
            if exc:
                raise exc
    finally:
        stop_event.set()
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)


async def hud_render_loop(
    event_queue: asyncio.Queue[GeminiEvent],
    output_audio_queue: asyncio.Queue[bytes],
    g2: EvenG2BleClient,
    config: BridgeConfig,
    stop_event: asyncio.Event,
) -> None:
    layout = TextLayoutManager(config.hud.columns, config.hud.lines)
    state = HudTextState()
    last_frame = ""

    while not stop_event.is_set():
        try:
            event = await asyncio.wait_for(event_queue.get(), timeout=0.2)
        except asyncio.TimeoutError:
            continue

        if event.output_audio:
            if config.gemini.play_output_audio:
                await output_audio_queue.put(event.output_audio)
            else:
                log.debug("discarded output audio len=%d because playback is disabled", len(event.output_audio))

        state.apply(
            input_text=event.input_text,
            output_text=event.output_text,
            mode=config.hud.transcript_mode,
        )
        frame = layout.render(state)
        if frame == last_frame:
            continue
        last_frame = frame
        log.info("HUD update PL=%s EN=%s", bool(event.input_text), bool(event.output_text))
        await g2.send_hud_text(frame)


def _install_signal_handlers(stop_event: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        with suppress(NotImplementedError):
            loop.add_signal_handler(sig, stop_event.set)


def _g2_config_for_packetizer(config: BridgeConfig):
    if config.g2.layout_header:
        return config.g2
    if config.g2.dry_run:
        log.warning("using dry-run placeholder G2 layout header 0f01")
        return replace(config.g2, dry_run=True, layout_header=bytes.fromhex("0f01"))
    return config.g2
