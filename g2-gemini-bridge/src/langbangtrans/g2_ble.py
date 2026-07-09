from __future__ import annotations

import asyncio
import logging
import re
from dataclasses import dataclass
from typing import Optional

from .config import G2BleConfig
from .g2_protocol import G2Packetizer

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class CandidateG2:
    address: str
    name: str
    service_uuids: tuple[str, ...]
    rssi: Optional[int] = None


class EvenG2BleClient:
    """Persistent BLE client for direct Even G2 writes.

    The public Even Hub SDK does not expose these UUIDs. The class is built so
    the discovered profile can be injected through environment variables and
    replayed without changing the bridge logic.
    """

    def __init__(self, config: G2BleConfig, packetizer: G2Packetizer) -> None:
        self.config = config
        self.packetizer = packetizer
        self._client = None
        self._write_lock = asyncio.Lock()
        self._connected = asyncio.Event()
        self._stop_event: Optional[asyncio.Event] = None

    @staticmethod
    async def scan(config: G2BleConfig, timeout: float = 8.0) -> list[CandidateG2]:
        from bleak import BleakScanner

        matcher = re.compile(config.name_regex, re.IGNORECASE)
        devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
        candidates: list[CandidateG2] = []
        for device, advertisement in devices.values():
            name = device.name or advertisement.local_name or ""
            service_uuids = tuple(advertisement.service_uuids or ())
            matches_name = bool(name and matcher.search(name))
            service_uuid_set = {uuid.lower() for uuid in service_uuids}
            matches_service = bool(config.service_uuid and config.service_uuid.lower() in service_uuid_set)
            if matches_name or matches_service:
                candidates.append(
                    CandidateG2(
                        address=device.address,
                        name=name or "(unnamed)",
                        service_uuids=service_uuids,
                        rssi=getattr(advertisement, "rssi", None),
                    )
                )
        return candidates

    async def run_connection_loop(self, stop_event: asyncio.Event) -> None:
        self._stop_event = stop_event
        if self.config.dry_run:
            log.info("G2 BLE dry-run enabled; packets will be logged instead of written")
            await stop_event.wait()
            return

        missing = self.config.missing_hardware_fields()
        if missing:
            raise RuntimeError(f"G2 BLE config incomplete: {', '.join(missing)}")

        backoff = 1.0
        while not stop_event.is_set():
            try:
                await self._connect_once(stop_event)
                backoff = 1.0
            except asyncio.CancelledError:
                raise
            except Exception:
                log.exception("G2 BLE connection failed")
                self._connected.clear()

            if stop_event.is_set():
                break
            log.info("reconnecting to G2 in %.1fs", backoff)
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, 30.0)

    async def _connect_once(self, stop_event: asyncio.Event) -> None:
        from bleak import BleakClient

        candidates = await self.scan(self.config, timeout=8.0)
        if not candidates:
            raise RuntimeError("no Even G2 BLE candidates found")

        target = candidates[0]
        log.info("connecting to G2 address=%s name=%s rssi=%s", target.address, target.name, target.rssi)

        def disconnected_callback(_client) -> None:  # noqa: ANN001
            log.warning("G2 disconnected")
            self._connected.clear()

        async with BleakClient(target.address, disconnected_callback=disconnected_callback) as client:
            self._client = client
            await self._start_notifications()
            await self._send_init_sequence()
            self._connected.set()
            log.info("G2 BLE ready")
            while client.is_connected and not stop_event.is_set():
                await asyncio.sleep(0.2)
        self._connected.clear()
        self._client = None

    async def _start_notifications(self) -> None:
        assert self._client is not None

        def on_notify(_sender, data: bytearray) -> None:  # noqa: ANN001
            log.debug("G2 notify %s", bytes(data).hex(" "))

        await self._client.start_notify(self.config.notify_char_uuid, on_notify)
        log.info("G2 notifications enabled uuid=%s", self.config.notify_char_uuid)

    async def _send_init_sequence(self) -> None:
        assert self._client is not None
        for index, packet in enumerate(self.config.init_packets, start=1):
            log.debug("G2 init packet %d/%d len=%d", index, len(self.config.init_packets), len(packet))
            await self._client.write_gatt_char(self.config.write_char_uuid, packet, response=True)
            await asyncio.sleep(self.config.packet_delay_ms / 1000)
        log.info("G2 init sequence sent packets=%d", len(self.config.init_packets))

    async def send_hud_text(self, text: str) -> None:
        packets = self.packetizer.packetize_text_frame(text)
        if self.config.dry_run:
            for index, packet in enumerate(packets, start=1):
                log.info("dry-run G2 packet %d/%d len=%d hex=%s", index, len(packets), len(packet), packet.hex(" "))
            return

        await self._connected.wait()
        assert self._client is not None

        async with self._write_lock:
            for index, packet in enumerate(packets, start=1):
                await self._client.write_gatt_char(self.config.write_char_uuid, packet, response=False)
                log.debug("G2 write packet %d/%d len=%d", index, len(packets), len(packet))
                await asyncio.sleep(self.config.packet_delay_ms / 1000)
