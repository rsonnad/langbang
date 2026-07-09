from __future__ import annotations

from dataclasses import dataclass

from .config import G2BleConfig


def crc16_ccitt_false(data: bytes) -> int:
    """CRC-16/CCITT-FALSE: poly 0x1021, init 0xFFFF, xorout 0x0000."""

    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc


@dataclass(frozen=True)
class G2Packetizer:
    """Builds firmware-bound HUD write packets.

    The proprietary G2 transport appears to separate "content" and "rendering
    matrix" channels by byte headers before the text payload. This packetizer
    keeps the header configurable because using a guessed value here is worse
    than refusing to talk to hardware.

    Packet shape:

        [layout_header][fragment_index][fragment_count][utf8_fragment][crc]

    The CRC covers every byte before the CRC trailer. The trailer encoding is
    configurable because firmware traces may represent it as ASCII hex or as a
    two-byte binary integer.
    """

    config: G2BleConfig

    def packetize_text_frame(self, text: str) -> list[bytes]:
        if not self.config.layout_header:
            raise ValueError("G2 layout header is missing")

        payload = text.encode("utf-8")
        crc_len = 4 if self.config.crc_encoding == "ascii-hex" else 2
        fragment_size = self.config.max_packet_bytes - len(self.config.layout_header) - 2 - crc_len
        if fragment_size <= 0:
            raise ValueError("G2 max packet bytes too small for header and CRC")

        fragments = [
            payload[offset : offset + fragment_size]
            for offset in range(0, len(payload), fragment_size)
        ] or [b""]
        if len(fragments) > 255:
            raise ValueError("G2 frame is too large to index in one-byte fragments")

        packets: list[bytes] = []
        total = len(fragments)
        for index, fragment in enumerate(fragments):
            body = self.config.layout_header + bytes([index, total]) + fragment
            packets.append(body + self._crc_trailer(body))
        return packets

    def _crc_trailer(self, body: bytes) -> bytes:
        crc = crc16_ccitt_false(body)
        if self.config.crc_encoding == "ascii-hex":
            return f"{crc:04X}".encode("ascii")
        if self.config.crc_encoding == "binary-be":
            return crc.to_bytes(2, "big")
        if self.config.crc_encoding == "binary-le":
            return crc.to_bytes(2, "little")
        raise ValueError(f"unsupported CRC encoding: {self.config.crc_encoding}")

