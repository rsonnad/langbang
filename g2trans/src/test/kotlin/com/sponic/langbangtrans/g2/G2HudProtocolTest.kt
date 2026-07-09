package com.sponic.langbangtrans.g2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-exact checks on the ported Even G2 EvenHub protocol. These run on the JVM with no
 * hardware, so we can be confident the wire bytes match the Mentra reference before ever
 * touching the physical glasses.
 */
class G2HudProtocolTest {

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    @Test
    fun crc16OfEmptyIsInitialValue() {
        assertEquals(0xFFFF, g2Crc16(ByteArray(0)))
    }

    @Test
    fun protobufIntAndStringEncoding() {
        assertEquals("08 00", ProtoWriter().apply { int32(1, 0) }.toByteArray().hex())
        assertEquals("10 04", ProtoWriter().apply { int32(2, 4) }.toByteArray().hex())
        assertEquals("18 C0 04", ProtoWriter().apply { int32(3, 576) }.toByteArray().hex())
        assertEquals(
            "52 06 74 65 78 74 2D 31",
            ProtoWriter().apply { string(10, "text-1") }.toByteArray().hex(),
        )
    }

    @Test
    fun authCmdMatchesReferenceBytes() {
        assertEquals("08 04 10 00 1A 04 08 01 10 04", DevSettingsProto.authCmd(0).hex())
    }

    @Test
    fun singlePacketFraming() {
        val payload = DevSettingsProto.authCmd(0)
        val packets = G2Transport.buildPackets(syncId = 0, serviceId = 0x80, payload = payload)
        assertEquals(1, packets.size)
        val p = packets[0]
        assertEquals(8 + payload.size + 2, p.size)
        assertEquals(0xAA, p[0].toInt() and 0xFF) // header
        assertEquals(0x21, p[1].toInt() and 0xFF) // dest<<4 | source
        assertEquals(0x00, p[2].toInt() and 0xFF) // syncId
        assertEquals(payload.size + 2, p[3].toInt() and 0xFF) // payloadLen includes CRC
        assertEquals(1, p[4].toInt() and 0xFF) // totalPackets
        assertEquals(1, p[5].toInt() and 0xFF) // serialNum
        assertEquals(0x80, p[6].toInt() and 0xFF) // serviceId
        assertEquals(0x00, p[7].toInt() and 0xFF) // status, reserveFlag off
        assertEquals(payload.hex(), p.copyOfRange(8, 8 + payload.size).hex())
        val crc = g2Crc16(payload)
        assertEquals(crc and 0xFF, p[p.size - 2].toInt() and 0xFF) // CRC low byte first
        assertEquals((crc shr 8) and 0xFF, p[p.size - 1].toInt() and 0xFF)
    }

    @Test
    fun reserveFlagSetsStatusBit5() {
        val packets = G2Transport.buildPackets(0, 0xE0, byteArrayOf(1, 2, 3), reserveFlag = true)
        assertEquals(0x20, packets[0][7].toInt() and 0xFF)
    }

    @Test
    fun fullFinalChunkAppendsCrcTrailerPacket() {
        val payload = ByteArray(236)
        val packets = G2Transport.buildPackets(0, 0xE0, payload, reserveFlag = true)
        assertEquals(2, packets.size)

        val first = packets[0]
        assertEquals(8 + 236, first.size)
        assertEquals(236, first[3].toInt() and 0xFF) // payloadLen, no CRC on a non-final packet
        assertEquals(2, first[4].toInt() and 0xFF) // totalPackets
        assertEquals(1, first[5].toInt() and 0xFF) // serialNum

        val second = packets[1]
        assertEquals(10, second.size) // 8 header + 0 chunk + 2 CRC
        assertEquals(2, second[3].toInt() and 0xFF) // payloadLen = CRC only
        assertEquals(2, second[4].toInt() and 0xFF) // totalPackets
        assertEquals(2, second[5].toInt() and 0xFF) // serialNum
    }

    @Test
    fun createPageEnvelopePrefix() {
        val container = EvenHubProto.textContainerProperty(
            x = 0,
            y = 0,
            width = 576,
            height = 288,
            paddingLength = 4,
            containerID = 1,
            containerName = "text-1",
            isEventCapture = true,
            content = "hi",
        )
        val msg = EvenHubProto.createPageMessage(listOf(container), magicRandom = 7)
        // cmd=CREATE_STARTUP_PAGE(0), magic=7, field-3 sub-message tag 0x1A
        assertEquals("08 00 10 07 1A", msg.copyOfRange(0, 5).hex())
        assertTrue(msg.size > container.size)
    }

    @Test
    fun updateTextEnvelopePrefix() {
        val msg = EvenHubProto.updateTextMessage(containerID = 1, content = "hi")
        // cmd=UPDATE_TEXT_DATA(5), magic=0, field-9 sub-message tag 0x4A
        assertEquals("08 05 10 00 4A", msg.copyOfRange(0, 5).hex())
    }

    @Test
    fun decodeSinglePacketRoundTrip() {
        val payload = byteArrayOf(0x08, 0x04, 0x1A, 0x02, 0x08, 0x01)
        val frame = G2Transport.buildPackets(syncId = 0, serviceId = 0x80, payload = payload)[0]
        val decoded = decodeSinglePacket(frame)
        assertEquals(0x80, decoded?.first)
        assertEquals(payload.hex(), decoded?.second?.hex())
    }

    @Test
    fun decodeRejectsMalformedFrame() {
        assertEquals(null, decodeSinglePacket(byteArrayOf(0x00, 0x01, 0x02)))
        // wrong header
        assertEquals(null, decodeSinglePacket(ByteArray(12) { 0x00 }))
    }

    @Test
    fun headUpSwitchBytes() {
        // pkg{1:DEVICE_RECEIVE_INFO(1), 2:magic(0), 3:info{4:headUp{1:1}}}
        assertEquals("08 01 10 00 1A 04 22 02 08 01", G2SettingProto.setHeadUpSwitch(0, true).hex())
    }

    @Test
    fun authSuccessDetection() {
        val ok = ProtoWriter().apply {
            int32(1, 4)
            message(3, ProtoWriter().apply { int32(1, 1) }.toByteArray())
        }.toByteArray()
        val notAuthed = ProtoWriter().apply {
            int32(1, 4)
            message(3, ProtoWriter().apply { int32(1, 0) }.toByteArray())
        }.toByteArray()
        val otherCmd = ProtoWriter().apply { int32(1, 128) }.toByteArray()

        assertTrue(isAuthSuccess(ok))
        assertTrue(!isAuthSuccess(notAuthed))
        assertTrue(!isAuthSuccess(otherCmd))
    }
}
