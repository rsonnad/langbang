package com.sponic.langbangtrans.g2

import java.io.ByteArrayOutputStream

/**
 * Real Even Realities G2 "EvenHub" BLE protocol.
 *
 * Ported byte-for-byte from the Mentra Community reference implementation
 * (MentraOS `dev` branch, `G2.kt`):
 * https://github.com/Mentra-Community/MentraOS/blob/dev/mobile/modules/bluetooth-sdk/android/src/main/java/com/mentra/bluetoothsdk/sgcs/G2.kt
 *
 * This is the real wire protocol, not the provisional made-up framing used during early bring-up.
 * Deliberately free of Android dependencies so it can be unit-tested on the JVM
 * (see `G2HudProtocolTest`).
 *
 * Packet frame:
 *   byte 0      : 0xAA (header)
 *   byte 1      : (DEST_GLASSES << 4) | SOURCE_PHONE = 0x21
 *   byte 2      : syncId (per-payload, increments)
 *   byte 3      : payloadLen (chunk size, +2 on the final packet for the CRC)
 *   byte 4      : totalPackets
 *   byte 5      : serialNum (1-based)
 *   byte 6      : serviceId
 *   byte 7      : status (bit5 = reserveFlag)
 *   byte 8..    : payload chunk
 *   final pkt   : CRC16(full payload), little-endian (low byte, high byte)
 */

internal object G2Ble {
    const val HEADER_BYTE: Int = 0xAA
    const val SOURCE_PHONE: Int = 1
    const val DEST_GLASSES: Int = 2

    /** Single source/dest address byte: dest in the high nibble, source in the low. */
    const val ADDR_BYTE: Int = (DEST_GLASSES shl 4) or SOURCE_PHONE // 0x21

    const val MAX_PACKET_PAYLOAD: Int = 236
}

/** ServiceID values from Even's `service_id_def.proto` (only what we use here). */
internal object G2Service {
    const val G2_SETTING: Int = 0x09
    const val DEVICE_SETTINGS: Int = 0x80
    const val EVEN_HUB: Int = 0xE0
}

/** g2_settingCommandId from `g2_setting.proto`. */
internal object G2SettingCmd {
    const val DEVICE_RECEIVE_INFO: Int = 1
}

/** EvenHub command IDs from `EvenHub.proto`. */
internal object EvenHubCmd {
    const val CREATE_STARTUP_PAGE: Int = 0
    const val UPDATE_TEXT_DATA: Int = 5
    const val SHUTDOWN_PAGE: Int = 9
    const val HEARTBEAT: Int = 12
}

/** DevCfgCommandId values from `dev_config_protocol.proto`. */
internal object DevCfgCmd {
    const val AUTHENTICATION: Int = 4
    const val PIPE_ROLE_CHANGE: Int = 5
    const val BASE_CONN_HEART_BEAT: Int = 14
    const val TIME_SYNC: Int = 128
}

/** CRC16 exactly as the Even firmware expects (Mentra `calcCRC16`). */
fun g2Crc16(data: ByteArray): Int {
    var crc = 0xFFFF
    for (byte in data) {
        val b = byte.toInt() and 0xFF
        crc = ((crc shr 8) or ((crc shl 8) and 0xFF00)) xor b
        crc = crc xor ((crc and 0xFF) shr 4)
        crc = crc xor ((crc shl 12) and 0xFFFF)
        crc = crc xor (((crc and 0xFF) shl 5) and 0xFFFF)
    }
    return crc and 0xFFFF
}

/** Minimal protobuf writer — the subset of wire types EvenHub messages need. */
class ProtoWriter {
    private val stream = ByteArrayOutputStream()

    fun varint(value: Long) {
        var v = value
        // Unsigned comparison so sign-extended negatives still terminate.
        while (v.toULong() > 0x7FuL) {
            stream.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        stream.write((v and 0x7F).toInt())
    }

    /** wire type 0 (varint). */
    fun int32(field: Int, value: Int) {
        varint((field shl 3).toLong())
        varint(value.toLong())
    }

    fun bool(field: Int, value: Boolean) = int32(field, if (value) 1 else 0)

    /** wire type 2 (length-delimited) — UTF-8 string. */
    fun string(field: Int, value: String) {
        varint(((field shl 3) or 2).toLong())
        val utf8 = value.toByteArray(Charsets.UTF_8)
        varint(utf8.size.toLong())
        stream.write(utf8)
    }

    /** wire type 2 (length-delimited) — embedded message bytes. */
    fun message(field: Int, sub: ByteArray) {
        varint(((field shl 3) or 2).toLong())
        varint(sub.size.toLong())
        stream.write(sub)
    }

    fun toByteArray(): ByteArray = stream.toByteArray()
}

/** EvenHub (service 0xE0) protobuf message builders. */
object EvenHubProto {
    /** A single text container's property message (rect + content). */
    fun textContainerProperty(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        borderWidth: Int = 0,
        borderColor: Int = 0,
        borderRadius: Int = 0,
        paddingLength: Int = 0,
        containerID: Int,
        containerName: String? = null,
        isEventCapture: Boolean = false,
        content: String? = null,
    ): ByteArray {
        val w = ProtoWriter()
        w.int32(1, x)
        w.int32(2, y)
        w.int32(3, width)
        w.int32(4, height)
        w.int32(5, borderWidth)
        w.int32(6, borderColor)
        w.int32(7, borderRadius)
        w.int32(8, paddingLength)
        w.int32(9, containerID)
        containerName?.let { w.string(10, it) }
        w.int32(11, if (isEventCapture) 1 else 0)
        content?.let { w.string(12, it) }
        return w.toByteArray()
    }

    private fun startupPageContainer(total: Int, textContainers: List<ByteArray>): ByteArray {
        val w = ProtoWriter()
        w.int32(1, total)
        for (tc in textContainers) w.message(3, tc)
        return w.toByteArray()
    }

    /** Wrap a command payload in the evenhub_main_msg_ctx envelope (cmd, magic, sub-message). */
    private fun envelope(cmd: Int, subField: Int, sub: ByteArray, magicRandom: Int): ByteArray {
        val w = ProtoWriter()
        w.int32(1, cmd)
        w.int32(2, magicRandom)
        w.message(subField, sub)
        return w.toByteArray()
    }

    /** Create a startup page from text containers (content is embedded, so it renders immediately). */
    fun createPageMessage(textContainers: List<ByteArray>, magicRandom: Int): ByteArray {
        val createMsg = startupPageContainer(textContainers.size, textContainers)
        return envelope(EvenHubCmd.CREATE_STARTUP_PAGE, 3, createMsg, magicRandom)
    }

    /** Update the text of an already-created container. Mentra sends magic=0 for updates. */
    fun updateTextMessage(containerID: Int, content: String): ByteArray {
        val contentLength = content.toByteArray(Charsets.UTF_8).size
        val upgrade = ProtoWriter().apply {
            int32(1, containerID)
            int32(3, 0) // contentOffset
            int32(4, contentLength)
            string(5, content)
        }.toByteArray()
        return envelope(EvenHubCmd.UPDATE_TEXT_DATA, 9, upgrade, magicRandom = 0)
    }

    /** Tear the current page down (Mentra sends magic=0). */
    fun shutdownMessage(exitMode: Int = 0): ByteArray {
        val msg = ProtoWriter().apply { int32(1, exitMode) }.toByteArray()
        return envelope(EvenHubCmd.SHUTDOWN_PAGE, 11, msg, magicRandom = 0)
    }

    /** EvenHub keep-alive. Mentra sends this every ~10s to hold the foreground page + display. */
    fun heartbeatMessage(magicRandom: Int): ByteArray =
        envelope(EvenHubCmd.HEARTBEAT, 14, ByteArray(0), magicRandom)
}

/** DevSettings (service 0x80) auth/startup protobuf builders. */
object DevSettingsProto {
    fun authCmd(magicRandom: Int): ByteArray {
        val auth = ProtoWriter().apply {
            bool(1, true) // secAuth
            int32(2, 4) // phoneType = PHONE_ANDROID
        }.toByteArray()
        return ProtoWriter().apply {
            int32(1, DevCfgCmd.AUTHENTICATION)
            int32(2, magicRandom)
            message(3, auth)
        }.toByteArray()
    }

    fun pipeRoleChange(magicRandom: Int): ByteArray {
        val role = ProtoWriter().apply { int32(1, 1) }.toByteArray() // asCmdRole = RIGHT
        return ProtoWriter().apply {
            int32(1, DevCfgCmd.PIPE_ROLE_CHANGE)
            int32(2, magicRandom)
            message(4, role)
        }.toByteArray()
    }

    /**
     * TimeSync: f1 = (Unix seconds + TZ offset seconds) as Int32. The firmware ignores a separate
     * TZ field, so the timestamp is pre-shifted to make UTC interpretation read as local time.
     * Clock is injected (not read from the system) so this stays Android-free and testable.
     */
    fun timeSync(magicRandom: Int, nowMillis: Long, tzOffsetMillis: Int): ByteArray {
        val nowSec = nowMillis / 1000
        val tzSec = (tzOffsetMillis / 1000).toLong()
        val ts = ProtoWriter().apply { int32(1, (nowSec + tzSec).toInt()) }.toByteArray()
        return ProtoWriter().apply {
            int32(1, DevCfgCmd.TIME_SYNC)
            int32(2, magicRandom)
            message(128, ts)
        }.toByteArray()
    }

    /** DevSettings keep-alive (empty BaseConnHeartBeat). Mentra sends this every ~5s. */
    fun baseHeartbeat(magicRandom: Int): ByteArray =
        ProtoWriter().apply {
            int32(1, DevCfgCmd.BASE_CONN_HEART_BEAT)
            int32(2, magicRandom)
            message(13, ByteArray(0))
        }.toByteArray()
}

/** G2 settings (service 0x09) — head-up display control, used to wake/keep the HUD on. */
object G2SettingProto {
    private fun packageInfo(magicRandom: Int, deviceReceiveInfo: ByteArray): ByteArray =
        ProtoWriter().apply {
            int32(1, G2SettingCmd.DEVICE_RECEIVE_INFO)
            int32(2, magicRandom)
            message(3, deviceReceiveInfo) // deviceReceiveInfoFromApp
        }.toByteArray()

    /** Turn the head-up display on/off (DeviceReceive_Head_UP_Setting.headUpSwitch). */
    fun setHeadUpSwitch(magicRandom: Int, enabled: Boolean): ByteArray {
        val headUp = ProtoWriter().apply { int32(1, if (enabled) 1 else 0) }.toByteArray()
        val info = ProtoWriter().apply { message(4, headUp) }.toByteArray() // deviceReceiveHeadUpSetting
        return packageInfo(magicRandom, info)
    }

    /** Head-up activation angle (0..60). Lower = display turns on with less upward tilt (~always-on). */
    fun setHeadUpAngle(magicRandom: Int, angle: Int): ByteArray {
        val headUp = ProtoWriter().apply { int32(2, angle.coerceIn(0, 60)) }.toByteArray()
        val info = ProtoWriter().apply { message(4, headUp) }.toByteArray()
        return packageInfo(magicRandom, info)
    }
}

/** Splits a command payload into BLE packets with the Even transport frame + CRC. */
object G2Transport {
    fun buildPackets(
        syncId: Int,
        serviceId: Int,
        payload: ByteArray,
        reserveFlag: Boolean = false,
    ): List<ByteArray> {
        val maxPayload = G2Ble.MAX_PACKET_PAYLOAD

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + maxPayload, payload.size)
            chunks.add(payload.copyOfRange(offset, end))
            offset = end
        }
        if (chunks.isEmpty()) chunks.add(ByteArray(0))
        // A full final chunk leaves no room for the CRC, so append an empty trailer packet.
        if (chunks.last().size == maxPayload) chunks.add(ByteArray(0))

        val totalPackets = chunks.size
        val crc = g2Crc16(payload)
        val status = if (reserveFlag) 0x20 else 0x00

        return chunks.mapIndexed { i, chunk ->
            val serialNum = i + 1
            val isLast = serialNum == totalPackets
            val payloadLen = chunk.size + if (isLast) 2 else 0

            val out = ByteArrayOutputStream()
            out.write(G2Ble.HEADER_BYTE)
            out.write(G2Ble.ADDR_BYTE)
            out.write(syncId and 0xFF)
            out.write(payloadLen and 0xFF)
            out.write(totalPackets and 0xFF)
            out.write(serialNum and 0xFF)
            out.write(serviceId and 0xFF)
            out.write(status and 0xFF)
            out.write(chunk)
            if (isLast) {
                out.write(crc and 0xFF)
                out.write((crc shr 8) and 0xFF)
            }
            out.toByteArray()
        }
    }
}

/** Minimal protobuf reader — varint + length-delimited fields, enough to parse glasses responses. */
class ProtoReader(private val data: ByteArray) {
    private var offset = 0

    private fun readVarint(): Long? {
        var result = 0L
        var shift = 0
        while (offset < data.size) {
            val b = data[offset].toInt() and 0xFF
            offset++
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) return null
        }
        return null
    }

    /** field number -> Int (varint) or ByteArray (length-delimited). */
    fun parseFields(): Map<Int, Any> {
        val fields = mutableMapOf<Int, Any>()
        while (offset < data.size) {
            val tag = readVarint() ?: break
            val field = (tag shr 3).toInt()
            when ((tag and 0x7).toInt()) {
                0 -> readVarint()?.let { fields[field] = it.toInt() }
                2 -> {
                    val len = readVarint()?.toInt() ?: break
                    if (len < 0 || offset + len > data.size) break
                    fields[field] = data.copyOfRange(offset, offset + len)
                    offset += len
                }
                1 -> offset += 8
                5 -> offset += 4
                else -> break
            }
        }
        return fields
    }
}

/**
 * Decode a single-packet response frame from the glasses into (serviceId, payload).
 * Returns null if it isn't a well-formed, single-packet, success frame. Auth/heartbeat
 * responses are single-packet, which is all we need to gate on for the text milestone.
 */
fun decodeSinglePacket(raw: ByteArray): Pair<Int, ByteArray>? {
    if (raw.size < 8) return null
    if ((raw[0].toInt() and 0xFF) != G2Ble.HEADER_BYTE) return null
    val payloadLen = raw[3].toInt() and 0xFF
    val totalPackets = raw[4].toInt() and 0xFF
    val serialNum = raw[5].toInt() and 0xFF
    val serviceId = raw[6].toInt() and 0xFF
    val status = raw[7].toInt() and 0xFF
    if (totalPackets != 1 || serialNum != 1) return null // only single-packet frames here
    if (((status shr 1) and 0x0F) != 0) return null // non-zero resultCode = error
    val end = 8 + payloadLen - 2 // final packet carries 2 CRC bytes
    if (end < 8 || end > raw.size) return null
    return serviceId to raw.copyOfRange(8, end)
}

/** True if a DEVICE_SETTINGS payload is an AUTHENTICATION response with secAuth set. */
fun isAuthSuccess(payload: ByteArray): Boolean {
    val fields = ProtoReader(payload).parseFields()
    if ((fields[1] as? Int) != DevCfgCmd.AUTHENTICATION) return false
    val auth = fields[3] as? ByteArray ?: return false
    return ((ProtoReader(auth).parseFields()[1] as? Int) ?: 0) != 0 // secAuth
}

/** Per-connection counters: syncId per payload, magicRandom per command. Both wrap at a byte. */
class G2SyncState {
    private var syncId = 0
    private var magic = 0

    fun nextSyncId(): Int {
        val id = syncId and 0xFF
        syncId = (syncId + 1) and 0xFF
        return id
    }

    fun nextMagic(): Int {
        val v = magic and 0xFF
        magic = (magic + 1) and 0xFF
        return v
    }
}
