from __future__ import annotations

import asyncio
import base64
import json
import logging
from dataclasses import dataclass
from typing import Any, Iterator, Optional

from .config import GeminiConfig

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class GeminiEvent:
    input_text: Optional[str] = None
    output_text: Optional[str] = None
    output_audio: Optional[bytes] = None
    input_language_code: Optional[str] = None
    output_language_code: Optional[str] = None
    total_tokens: Optional[int] = None
    raw: Optional[dict[str, Any]] = None

    @property
    def has_payload(self) -> bool:
        return bool(self.input_text or self.output_text or self.output_audio)


class GeminiLiveClient:
    """Raw WebSocket client for Gemini Live Translate."""

    def __init__(self, config: GeminiConfig) -> None:
        self.config = config

    def setup_message(self) -> dict[str, Any]:
        return {
            "setup": {
                "model": f"models/{self.config.model}",
                "generationConfig": {
                    "responseModalities": list(self.config.response_modalities),
                    "inputAudioTranscription": {},
                    "outputAudioTranscription": {},
                    "translationConfig": {
                        "targetLanguageCode": self.config.target_language_code,
                        "echoTargetLanguage": self.config.echo_target_language,
                    },
                },
            }
        }

    @staticmethod
    def audio_message(chunk: bytes, sample_rate: int = 16000) -> dict[str, Any]:
        return {
            "realtimeInput": {
                "audio": {
                    "data": base64.b64encode(chunk).decode("ascii"),
                    "mimeType": f"audio/pcm;rate={sample_rate}",
                }
            }
        }

    async def run(
        self,
        audio_queue: asyncio.Queue[bytes],
        event_queue: asyncio.Queue[GeminiEvent],
        stop_event: asyncio.Event,
        *,
        capture_frame_ms: int,
        sample_rate: int,
    ) -> None:
        import websockets

        frames_per_send = max(1, self.config.gemini_audio_chunk_ms // capture_frame_ms)
        log.info(
            "connecting Gemini Live model=%s endpoint=%s modalities=%s target=%s frames_per_send=%s",
            self.config.model,
            self.config.ws_endpoint,
            ",".join(self.config.response_modalities),
            self.config.target_language_code,
            frames_per_send,
        )

        async with websockets.connect(self.config.websocket_url, ping_interval=20, ping_timeout=20) as websocket:
            await websocket.send(json.dumps(self.setup_message()))
            log.info("Gemini Live setup sent")

            sender = asyncio.create_task(
                self._send_audio_loop(
                    websocket,
                    audio_queue,
                    stop_event,
                    frames_per_send=frames_per_send,
                    sample_rate=sample_rate,
                )
            )
            receiver = asyncio.create_task(self._receive_loop(websocket, event_queue, stop_event))
            try:
                await asyncio.wait({sender, receiver}, return_when=asyncio.FIRST_EXCEPTION)
            finally:
                sender.cancel()
                receiver.cancel()

    async def _send_audio_loop(
        self,
        websocket,  # noqa: ANN001
        audio_queue: asyncio.Queue[bytes],
        stop_event: asyncio.Event,
        *,
        frames_per_send: int,
        sample_rate: int,
    ) -> None:
        pending = bytearray()
        frames = 0
        sent_bytes = 0
        while not stop_event.is_set():
            try:
                chunk = await asyncio.wait_for(audio_queue.get(), timeout=0.2)
            except asyncio.TimeoutError:
                continue
            pending.extend(chunk)
            frames += 1
            if frames < frames_per_send:
                continue

            await websocket.send(json.dumps(self.audio_message(bytes(pending), sample_rate=sample_rate)))
            sent_bytes += len(pending)
            if sent_bytes % (sample_rate * 2 * 10) == 0:
                log.debug("Gemini audio sent bytes=%d", sent_bytes)
            pending.clear()
            frames = 0

    async def _receive_loop(
        self,
        websocket,  # noqa: ANN001
        event_queue: asyncio.Queue[GeminiEvent],
        stop_event: asyncio.Event,
    ) -> None:
        async for message in websocket:
            if stop_event.is_set():
                return
            try:
                response = json.loads(message)
            except json.JSONDecodeError:
                log.warning("Gemini non-JSON message len=%d", len(message))
                continue
            for event in parse_gemini_events(response):
                if event.total_tokens is not None:
                    log.info("Gemini tokens total=%s", event.total_tokens)
                if event.has_payload:
                    await event_queue.put(event)


def parse_gemini_events(response: dict[str, Any]) -> Iterator[GeminiEvent]:
    """Yield normalized transcript/audio events from a Live API message."""

    events: list[GeminiEvent] = []
    server_content = response.get("serverContent") or response.get("server_content") or {}
    usage = response.get("usageMetadata") or response.get("usage_metadata") or {}
    total_tokens = usage.get("totalTokenCount") or usage.get("total_token_count")

    input_transcription = _get_transcription(server_content, "input")
    output_transcription = _get_transcription(server_content, "output")
    if input_transcription or output_transcription or total_tokens is not None:
        events.append(
            GeminiEvent(
                input_text=(input_transcription or {}).get("text"),
                output_text=(output_transcription or {}).get("text"),
                input_language_code=(input_transcription or {}).get("languageCode")
                or (input_transcription or {}).get("language_code"),
                output_language_code=(output_transcription or {}).get("languageCode")
                or (output_transcription or {}).get("language_code"),
                total_tokens=total_tokens,
                raw=response,
            )
        )

    model_turn = server_content.get("modelTurn") or server_content.get("model_turn") or {}
    parts = model_turn.get("parts") or []
    for part in parts:
        inline = part.get("inlineData") or part.get("inline_data")
        if not inline:
            continue
        data = inline.get("data")
        if not data:
            continue
        try:
            audio = base64.b64decode(data)
        except (ValueError, TypeError):
            log.warning("Gemini inline audio was not base64")
            continue
        events.append(GeminiEvent(output_audio=audio, raw=response))

    for event in events:
        yield event


def _get_transcription(server_content: dict[str, Any], side: str) -> Optional[dict[str, Any]]:
    camel = f"{side}Transcription"
    snake = f"{side}_transcription"
    value = server_content.get(camel) or server_content.get(snake)
    if isinstance(value, dict):
        return value
    return None
