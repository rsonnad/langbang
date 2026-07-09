from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional


def _env(name: str, default: str = "") -> str:
    return os.environ.get(f"LANGBANGTRANS_{name}", default).strip()


def _env_bool(name: str, default: bool = False) -> bool:
    raw = _env(name, "true" if default else "false").lower()
    return raw in {"1", "true", "yes", "y", "on"}


def _env_int(name: str, default: int) -> int:
    raw = _env(name, str(default))
    return int(raw)


def _split_csv(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _parse_hex_packets(raw: str) -> list[bytes]:
    packets: list[bytes] = []
    for item in _split_csv(raw):
        cleaned = item.replace(" ", "").replace(":", "").replace("-", "")
        if len(cleaned) % 2:
            raise ValueError(f"hex packet has odd length: {item!r}")
        packets.append(bytes.fromhex(cleaned))
    return packets


def _load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


@dataclass(frozen=True)
class GeminiConfig:
    api_key: str
    model: str = "gemini-3.5-live-translate-preview"
    ws_endpoint: str = (
        "wss://generativelanguage.googleapis.com/ws/"
        "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    )
    target_language_code: str = "en"
    echo_target_language: bool = True
    response_modalities: tuple[str, ...] = ("AUDIO",)
    play_output_audio: bool = False
    gemini_audio_chunk_ms: int = 100
    output_sample_rate: int = 24000

    @property
    def websocket_url(self) -> str:
        separator = "&" if "?" in self.ws_endpoint else "?"
        return f"{self.ws_endpoint}{separator}key={self.api_key}"


@dataclass(frozen=True)
class AudioConfig:
    capture_sample_rate: int = 16000
    capture_frame_ms: int = 20
    input_device: Optional[str] = None
    output_device: Optional[str] = None


@dataclass(frozen=True)
class G2BleConfig:
    dry_run: bool = True
    name_regex: str = "Even G2|G2"
    service_uuid: str = ""
    write_char_uuid: str = ""
    notify_char_uuid: str = ""
    init_packets: tuple[bytes, ...] = field(default_factory=tuple)
    layout_header: bytes = b""
    max_packet_bytes: int = 180
    packet_delay_ms: int = 25
    crc_encoding: str = "ascii-hex"

    def missing_hardware_fields(self) -> list[str]:
        missing = []
        if not self.service_uuid:
            missing.append("LANGBANGTRANS_G2_SERVICE_UUID")
        if not self.write_char_uuid:
            missing.append("LANGBANGTRANS_G2_WRITE_CHAR_UUID")
        if not self.notify_char_uuid:
            missing.append("LANGBANGTRANS_G2_NOTIFY_CHAR_UUID")
        if len(self.init_packets) != 7:
            missing.append("LANGBANGTRANS_G2_INIT_PACKETS_HEX")
        if not self.layout_header:
            missing.append("LANGBANGTRANS_G2_LAYOUT_HEADER_HEX")
        return missing


@dataclass(frozen=True)
class HudConfig:
    columns: int = 28
    lines: int = 10
    transcript_mode: str = "append"


@dataclass(frozen=True)
class LoggingConfig:
    level: str = "INFO"
    file: Path = Path("logs/langbangtrans.log")


@dataclass(frozen=True)
class BridgeConfig:
    gemini: GeminiConfig
    audio: AudioConfig
    g2: G2BleConfig
    hud: HudConfig
    logging: LoggingConfig

    @classmethod
    def from_env(cls, dotenv: str | Path | None = ".env") -> "BridgeConfig":
        if dotenv:
            _load_dotenv(Path(dotenv))

        return cls(
            gemini=GeminiConfig(
                api_key=_env("GEMINI_API_KEY"),
                model=_env("GEMINI_MODEL", "gemini-3.5-live-translate-preview"),
                ws_endpoint=_env(
                    "GEMINI_WS_ENDPOINT",
                    (
                        "wss://generativelanguage.googleapis.com/ws/"
                        "google.ai.generativelanguage.v1beta.GenerativeService."
                        "BidiGenerateContent"
                    ),
                ),
                target_language_code=_env("TARGET_LANGUAGE_CODE", "en"),
                echo_target_language=_env_bool("ECHO_TARGET_LANGUAGE", True),
                response_modalities=tuple(
                    item.upper()
                    for item in _split_csv(_env("RESPONSE_MODALITIES", "AUDIO"))
                ),
                play_output_audio=_env_bool("PLAY_OUTPUT_AUDIO", False),
                gemini_audio_chunk_ms=_env_int("GEMINI_AUDIO_CHUNK_MS", 100),
                output_sample_rate=_env_int("OUTPUT_SAMPLE_RATE", 24000),
            ),
            audio=AudioConfig(
                capture_sample_rate=_env_int("CAPTURE_SAMPLE_RATE", 16000),
                capture_frame_ms=_env_int("CAPTURE_FRAME_MS", 20),
                input_device=_env("INPUT_DEVICE") or None,
                output_device=_env("OUTPUT_DEVICE") or None,
            ),
            g2=G2BleConfig(
                dry_run=_env_bool("DRY_RUN", True),
                name_regex=_env("G2_NAME_REGEX", "Even G2|G2"),
                service_uuid=_env("G2_SERVICE_UUID"),
                write_char_uuid=_env("G2_WRITE_CHAR_UUID"),
                notify_char_uuid=_env("G2_NOTIFY_CHAR_UUID"),
                init_packets=tuple(_parse_hex_packets(_env("G2_INIT_PACKETS_HEX"))),
                layout_header=bytes.fromhex(
                    _env("G2_LAYOUT_HEADER_HEX").replace(" ", "").replace(":", "")
                )
                if _env("G2_LAYOUT_HEADER_HEX")
                else b"",
                max_packet_bytes=_env_int("G2_MAX_PACKET_BYTES", 180),
                packet_delay_ms=_env_int("G2_PACKET_DELAY_MS", 25),
                crc_encoding=_env("G2_CRC_ENCODING", "ascii-hex"),
            ),
            hud=HudConfig(
                columns=_env_int("HUD_COLUMNS", 28),
                lines=_env_int("HUD_LINES", 10),
                transcript_mode=_env("TRANSCRIPT_MODE", "append"),
            ),
            logging=LoggingConfig(
                level=_env("LOG_LEVEL", "INFO"),
                file=Path(_env("LOG_FILE", "logs/langbangtrans.log")),
            ),
        )

    def validate_for_real_run(self) -> list[str]:
        problems: list[str] = []
        if not self.gemini.api_key:
            problems.append("LANGBANGTRANS_GEMINI_API_KEY is required")
        if not self.g2.dry_run:
            problems.extend(self.g2.missing_hardware_fields())
        if self.gemini.gemini_audio_chunk_ms % self.audio.capture_frame_ms:
            problems.append(
                "LANGBANGTRANS_GEMINI_AUDIO_CHUNK_MS must be divisible by "
                "LANGBANGTRANS_CAPTURE_FRAME_MS"
            )
        if self.hud.columns < 8 or self.hud.lines < 2:
            problems.append("HUD layout is too small to render PL/EN text")
        if self.g2.crc_encoding not in {"ascii-hex", "binary-be", "binary-le"}:
            problems.append("LANGBANGTRANS_G2_CRC_ENCODING must be ascii-hex, binary-be, or binary-le")
        return problems

    def redacted_lines(self) -> Iterable[str]:
        yield f"model={self.gemini.model}"
        yield f"ws_endpoint={self.gemini.ws_endpoint}"
        yield f"target_language_code={self.gemini.target_language_code}"
        yield f"response_modalities={','.join(self.gemini.response_modalities)}"
        yield f"play_output_audio={self.gemini.play_output_audio}"
        yield f"capture={self.audio.capture_sample_rate}Hz/{self.audio.capture_frame_ms}ms"
        yield f"gemini_chunk_ms={self.gemini.gemini_audio_chunk_ms}"
        yield f"g2_dry_run={self.g2.dry_run}"
        yield f"g2_service_uuid={'set' if self.g2.service_uuid else 'missing'}"
        yield f"g2_write_char_uuid={'set' if self.g2.write_char_uuid else 'missing'}"
        yield f"g2_notify_char_uuid={'set' if self.g2.notify_char_uuid else 'missing'}"
        yield f"g2_init_packets={len(self.g2.init_packets)}"
        yield f"g2_layout_header_bytes={len(self.g2.layout_header)}"

