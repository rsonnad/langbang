from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import Optional

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class AudioCaptureConfig:
    sample_rate: int
    frame_ms: int
    input_device: Optional[str] = None

    @property
    def blocksize(self) -> int:
        return int(self.sample_rate * self.frame_ms / 1000)


class AsyncPcmMicrophone:
    """Async wrapper around a PortAudio raw microphone stream.

    Gemini Live expects raw little-endian 16-bit mono PCM at 16 kHz. The stream
    callback runs on a PortAudio thread, so it only schedules queue insertion on
    the asyncio loop and never blocks on I/O.
    """

    def __init__(
        self,
        config: AudioCaptureConfig,
        output_queue: asyncio.Queue[bytes],
    ) -> None:
        self.config = config
        self.output_queue = output_queue
        self._stream = None

    async def run(self, stop_event: asyncio.Event) -> None:
        import sounddevice as sd

        loop = asyncio.get_running_loop()

        def callback(indata, frames, time, status) -> None:  # noqa: ANN001
            if status:
                log.warning("audio capture status=%s", status)
            chunk = bytes(indata)
            try:
                loop.call_soon_threadsafe(self.output_queue.put_nowait, chunk)
            except asyncio.QueueFull:
                log.warning("audio capture queue full; dropping %d bytes", len(chunk))

        log.info(
            "opening microphone sample_rate=%s blocksize=%s frame_ms=%s device=%s",
            self.config.sample_rate,
            self.config.blocksize,
            self.config.frame_ms,
            self.config.input_device or "default",
        )

        with sd.RawInputStream(
            samplerate=self.config.sample_rate,
            blocksize=self.config.blocksize,
            channels=1,
            dtype="int16",
            device=self.config.input_device or None,
            callback=callback,
        ) as stream:
            self._stream = stream
            await stop_event.wait()


class OutputAudioPlayer:
    """Plays translated Gemini audio chunks through the system output device."""

    def __init__(
        self,
        sample_rate: int,
        input_queue: asyncio.Queue[bytes],
        output_device: Optional[str] = None,
    ) -> None:
        self.sample_rate = sample_rate
        self.input_queue = input_queue
        self.output_device = output_device

    async def run(self, stop_event: asyncio.Event) -> None:
        import sounddevice as sd

        log.info(
            "opening output audio sample_rate=%s device=%s",
            self.sample_rate,
            self.output_device or "default",
        )
        with sd.RawOutputStream(
            samplerate=self.sample_rate,
            blocksize=0,
            channels=1,
            dtype="int16",
            device=self.output_device or None,
        ) as stream:
            while not stop_event.is_set():
                try:
                    chunk = await asyncio.wait_for(self.input_queue.get(), timeout=0.2)
                except asyncio.TimeoutError:
                    continue
                stream.write(chunk)

