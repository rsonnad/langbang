package com.sponic.langbangtrans.g2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.sponic.langbangtrans.BridgeStatusBus
import com.sponic.langbangtrans.G2Config
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Result of [G2HudLink.connect]: whether at least one lens is writable, plus a diagnostic report. */
data class G2HudConnectResult(val ok: Boolean, val report: String, val writableCount: Int)

/**
 * Persistent connection to the Even G2 HUD over the real EvenHub protocol ([G2HudProtocol]).
 *
 * Lifecycle: [connect] scans both lenses, connects, bumps MTU, enables notifications, runs the
 * authenticated startup, wakes the head-up display, and creates one text page. Then [updateHud]
 * replaces the page text (e.g. live transcripts), [runKeepAlive] (run in a background coroutine)
 * keeps the session + display alive with heartbeats, and [close] tears it down.
 *
 * This is the reusable core extracted from the one-shot test sender; both the "send test text"
 * button and the live translation bridge drive the same connection.
 */
class G2HudLink(
    private val context: Context,
    private val config: G2Config,
) {
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(BluetoothManager::class.java)
    private val sync = G2SyncState()
    // Serializes ALL BLE writes (connect, updateHud, keepAlive) so concurrent coroutines never
    // issue overlapping gatt.writeCharacteristic calls — the Android BLE stack wants one at a time.
    private val writeMutex = Mutex()
    private var lenses: List<Lens> = emptyList()
    private var writable: List<Lens> = emptyList()

    // Latest HUD text, so the keep-alive loop can re-arm the page (and thus the head-up display)
    // on its own cadence without the caller re-supplying it.
    @Volatile
    private var lastText: String = ""
    @Volatile
    private var lastHudWriteMs: Long = 0L

    @SuppressLint("MissingPermission")
    private inner class Lens(val device: BluetoothDevice) {
        val name: String = device.safeName()
        val side: String = when {
            name.contains("_L_", ignoreCase = true) -> "L"
            name.contains("_R_", ignoreCase = true) -> "R"
            else -> "?"
        }
        var gatt: BluetoothGatt? = null
        var writeChar: BluetoothGattCharacteristic? = null
        var mtu: Int = 23
        val ready = CompletableDeferred<Boolean>()
        val authed = CompletableDeferred<Boolean>()
        val responses: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
    }

    /** Scan, connect both lenses, authenticate, wake the display, and create the initial page. */
    @SuppressLint("MissingPermission")
    suspend fun connect(initialText: String): G2HudConnectResult {
        val report = StringBuilder()
        report.appendLine("LangBangTrans G2 HUD connect")
        report.appendLine("timestamp=${ISO_FORMAT.format(Date())}")
        report.appendLine("nameRegex=${config.nameRegex}")
        report.appendLine()

        val devices = scanForLenses()
        report.appendLine("candidateCount=${devices.size}")
        for (device in devices) {
            report.appendLine("  ${device.safeName()} ${device.address} bond=${device.bondState}")
        }
        report.appendLine()
        if (devices.isEmpty()) {
            report.appendLine("No Even G2 devices found. Keep the glasses awake and close the official Even app.")
            return result(report, ok = false)
        }

        lenses = devices.map { Lens(it) }
        for (lens in lenses) {
            BridgeStatusBus.set("G2 connect", "Connecting ${lens.side}: ${lens.name}")
            connectLens(lens)
        }
        for (lens in lenses) {
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { lens.ready.await() } ?: false
            report.appendLine(
                "lens=${lens.side} ${lens.name} ready=$ok mtu=${lens.mtu} writeChar=${lens.writeChar != null}"
            )
        }
        report.appendLine()

        writable = lenses.filter { it.writeChar != null }
        if (writable.isEmpty()) {
            report.appendLine("No writable lens after connect; aborting.")
            return result(report, ok = false)
        }

        BridgeStatusBus.set("G2 connect", "Auth + page to ${writable.size} lens(es)")
        runAuthSequence(writable, report)
        awaitAuth(writable, report)
        wakeDisplay(writable, report)
        createPage(writable, initialText, report)
        lastText = initialText
        report.appendLine()
        report.appendLine("Connected — ${writable.size} lens(es) live on HUD.")
        return result(report, ok = true)
    }

    /** Replace the HUD text (container 1). Safe no-op until [connect] has a writable lens. */
    suspend fun updateHud(text: String) {
        lastText = text
        if (writable.isEmpty()) return
        emitPayload(G2Service.EVEN_HUB, EvenHubProto.updateTextMessage(TEXT_CONTAINER_ID, text), true, writable)
        lastHudWriteMs = System.currentTimeMillis()
    }

    /**
     * Re-create the page (vs. just updating its text). The G2 head-up display powers the lens off
     * after a ~13s dwell that a plain text update does NOT reset — re-creating the page re-arms that
     * timer, so calling this on an interval shorter than the dwell keeps the display lit.
     */
    suspend fun refreshPage(text: String) {
        lastText = text
        if (writable.isEmpty()) return
        val container = EvenHubProto.textContainerProperty(
            x = 0,
            y = 0,
            width = 576,
            height = 288,
            paddingLength = 4,
            containerID = TEXT_CONTAINER_ID,
            containerName = "text-$TEXT_CONTAINER_ID",
            isEventCapture = true,
            content = text,
        )
        emitPayload(G2Service.EVEN_HUB, EvenHubProto.createPageMessage(listOf(container), sync.nextMagic()), true, writable)
        lastHudWriteMs = System.currentTimeMillis()
    }

    /**
     * Keep the session + display alive until cancelled: a DevSettings keep-alive every tick (~5s),
     * and an EvenHub keep-alive + head-up re-assert every other tick (~10s). Run in its own coroutine.
     */
    suspend fun runKeepAlive() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            tick++
            if (writable.isNotEmpty()) {
                // Timer/transcript updates normally keep text moving. Re-create the page only if no
                // HUD write has happened recently; frequent page recreation flashes the G2's
                // "connection lost" page between frames.
                val staleHud = System.currentTimeMillis() - lastHudWriteMs > PAGE_REFRESH_MS
                if (lastText.isNotEmpty() && staleHud) refreshPage(lastText)
                emitPayload(G2Service.DEVICE_SETTINGS, DevSettingsProto.baseHeartbeat(sync.nextMagic()), false, writable)
                if (tick % 2 == 1) {
                    emitPayload(G2Service.EVEN_HUB, EvenHubProto.heartbeatMessage(sync.nextMagic()), true, writable)
                    emitPayload(G2Service.G2_SETTING, G2SettingProto.setHeadUpSwitch(sync.nextMagic(), true), true, writable)
                }
            }
            delay(HEARTBEAT_TICK_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        for (lens in lenses) {
            runCatching { lens.gatt?.disconnect() }
            runCatching { lens.gatt?.close() }
        }
        lenses = emptyList()
        writable = emptyList()
    }

    private fun result(report: StringBuilder, ok: Boolean): G2HudConnectResult {
        val text = report.toString()
        val file = runCatching { writeReport(text) }.getOrNull()
        val full = if (file != null) "$text\nsavedPath=${file.absolutePath}\n" else text
        BridgeStatusBus.discovery(full)
        return G2HudConnectResult(ok = ok, report = full, writableCount = writable.size)
    }

    // ---------- Scan ----------

    @SuppressLint("MissingPermission")
    private suspend fun scanForLenses(): List<BluetoothDevice> =
        withTimeout(SCAN_TIMEOUT_MS + 1_000L) {
            suspendCancellableCoroutine { continuation ->
                val scanner = bluetoothManager.adapter?.bluetoothLeScanner
                val matcher = Regex(config.nameRegex, RegexOption.IGNORE_CASE)
                val found = linkedMapOf<String, BluetoothDevice>()
                for (device in bondedLensCandidates(matcher)) {
                    found[device.address] = device
                }
                if (found.size >= 2) {
                    BridgeStatusBus.set(
                        "G2 connect",
                        "Using bonded lenses: ${found.values.joinToString { it.safeName() }}",
                    )
                    continuation.resumeWith(Result.success(found.values.toList()))
                    return@suspendCancellableCoroutine
                }
                if (scanner == null) {
                    continuation.resumeWith(Result.success(found.values.toList()))
                    return@suspendCancellableCoroutine
                }
                var done = false
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.scanRecord?.deviceName ?: result.device.safeName()
                        if (!matcher.containsMatchIn(name)) return
                        found[result.device.address] = result.device
                        BridgeStatusBus.set(
                            "G2 connect",
                            "Found ${found.size}: ${found.values.joinToString { it.safeName() }}",
                        )
                        if (found.size >= 2 && !done) {
                            done = true
                            scanner.stopScan(this)
                            continuation.resumeWith(Result.success(found.values.toList()))
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (done) return
                        done = true
                        runCatching { scanner.stopScan(this) }
                        continuation.resumeWith(Result.success(found.values.toList()))
                    }
                }
                scanner.startScan(callback)
                continuation.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
                android.os.Handler(context.mainLooper).postDelayed({
                    if (!done) {
                        done = true
                        runCatching { scanner.stopScan(callback) }
                        continuation.resumeWith(Result.success(found.values.toList()))
                    }
                }, SCAN_TIMEOUT_MS)
            }
        }

    @SuppressLint("MissingPermission")
    private fun bondedLensCandidates(matcher: Regex): List<BluetoothDevice> =
        bluetoothManager.adapter?.bondedDevices
            ?.asSequence()
            ?.filter { matcher.containsMatchIn(it.safeName()) }
            ?.distinctBy { it.address }
            ?.sortedWith(compareBy<BluetoothDevice> {
                when {
                    it.safeName().contains("_L_", ignoreCase = true) -> 0
                    it.safeName().contains("_R_", ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.safeName() })
            ?.toList()
            ?: emptyList()

    // ---------- Connect ----------

    @SuppressLint("MissingPermission")
    private fun connectLens(lens: Lens) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    lens.gatt = gatt
                    val requested = runCatching { gatt.requestMtu(247) }.getOrDefault(false)
                    runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                    if (!requested) runCatching { gatt.discoverServices() }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!lens.ready.isCompleted) lens.ready.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                lens.mtu = mtu
                runCatching { gatt.discoverServices() }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!lens.ready.isCompleted) lens.ready.complete(false)
                    return
                }
                lens.writeChar = gatt.findCharacteristic(COMMAND_WRITE_UUID)
                val notify = gatt.findCharacteristic(COMMAND_NOTIFY_UUID)
                if (notify == null) {
                    if (!lens.ready.isCompleted) lens.ready.complete(lens.writeChar != null)
                    return
                }
                gatt.setCharacteristicNotification(notify, true)
                val cccd = notify.getDescriptor(CCCD_UUID)
                if (cccd == null) {
                    if (!lens.ready.isCompleted) lens.ready.complete(lens.writeChar != null)
                    return
                }
                val result = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (result != BluetoothStatusCodes.SUCCESS && !lens.ready.isCompleted) {
                    lens.ready.complete(lens.writeChar != null)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (descriptor.uuid == CCCD_UUID && !lens.ready.isCompleted) {
                    lens.ready.complete(lens.writeChar != null)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid != COMMAND_NOTIFY_UUID) return
                val decoded = decodeSinglePacket(value) ?: return
                val (serviceId, payload) = decoded
                if (serviceId == G2Service.DEVICE_SETTINGS && isAuthSuccess(payload)) {
                    lens.responses.add("authOk")
                    if (!lens.authed.isCompleted) lens.authed.complete(true)
                } else {
                    lens.responses.add("svc0x${serviceId.toString(16)}")
                }
            }
        }
        lens.gatt = lens.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    // ---------- Auth + page ----------

    private suspend fun runAuthSequence(lenses: List<Lens>, report: StringBuilder) {
        val leftTarget = lenses.firstOrNull { it.side == "L" } ?: lenses.getOrNull(0)
        val rightTarget = lenses.firstOrNull { it.side == "R" } ?: lenses.getOrNull(1) ?: lenses.getOrNull(0)

        leftTarget?.let {
            sendPayload(G2Service.DEVICE_SETTINGS, DevSettingsProto.authCmd(sync.nextMagic()), false, listOf(it), "authL", report)
            delay(STEP_DELAY_MS)
        }
        rightTarget?.let {
            sendPayload(G2Service.DEVICE_SETTINGS, DevSettingsProto.authCmd(sync.nextMagic()), false, listOf(it), "authR", report)
            delay(STEP_DELAY_MS)
            sendPayload(G2Service.DEVICE_SETTINGS, DevSettingsProto.pipeRoleChange(sync.nextMagic()), false, listOf(it), "pipeRole", report)
            delay(STEP_DELAY_MS)
        }
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault().getOffset(now)
        sendPayload(G2Service.DEVICE_SETTINGS, DevSettingsProto.timeSync(sync.nextMagic(), now, tz), false, lenses, "timeSync", report)
        delay(STEP_DELAY_MS + 100)
    }

    private suspend fun awaitAuth(lenses: List<Lens>, report: StringBuilder) {
        for (lens in lenses) {
            val confirmed = withTimeoutOrNull(AUTH_TIMEOUT_MS) { lens.authed.await() } ?: false
            val seen = lens.responses.toList().joinToString(",").ifEmpty { "none" }
            report.appendLine("auth ${lens.side}: confirmed=$confirmed responses=[$seen]")
        }
        delay(STEP_DELAY_MS + 300)
    }

    private suspend fun wakeDisplay(lenses: List<Lens>, report: StringBuilder) {
        sendPayload(G2Service.G2_SETTING, G2SettingProto.setHeadUpSwitch(sync.nextMagic(), true), true, lenses, "headUpOn", report)
        delay(STEP_DELAY_MS)
        sendPayload(G2Service.G2_SETTING, G2SettingProto.setHeadUpAngle(sync.nextMagic(), 0), true, lenses, "headUpAngle0", report)
        delay(STEP_DELAY_MS)
    }

    private suspend fun createPage(lenses: List<Lens>, text: String, report: StringBuilder) {
        val container = EvenHubProto.textContainerProperty(
            x = 0,
            y = 0,
            width = 576,
            height = 288,
            paddingLength = 4,
            containerID = TEXT_CONTAINER_ID,
            containerName = "text-$TEXT_CONTAINER_ID",
            isEventCapture = true,
            content = text,
        )
        val createPage = EvenHubProto.createPageMessage(listOf(container), sync.nextMagic())
        sendPayload(G2Service.EVEN_HUB, createPage, true, lenses, "createPage", report)
        delay(STEP_DELAY_MS + 200)
        sendPayload(G2Service.EVEN_HUB, EvenHubProto.updateTextMessage(TEXT_CONTAINER_ID, text), true, lenses, "updateText", report)
        lastHudWriteMs = System.currentTimeMillis()
        delay(STEP_DELAY_MS)
    }

    private suspend fun sendPayload(
        serviceId: Int,
        payload: ByteArray,
        reserveFlag: Boolean,
        targets: List<Lens>,
        label: String,
        report: StringBuilder,
    ) {
        report.appendLine("$label ${emitPayload(serviceId, payload, reserveFlag, targets)}")
    }

    private suspend fun emitPayload(
        serviceId: Int,
        payload: ByteArray,
        reserveFlag: Boolean,
        targets: List<Lens>,
    ): String = writeMutex.withLock {
        val syncId = sync.nextSyncId()
        val packets = G2Transport.buildPackets(syncId, serviceId, payload, reserveFlag)
        val results = StringBuilder()
        for ((index, packet) in packets.withIndex()) {
            for (lens in targets) {
                results.append("${lens.side}p${index + 1}=${writeToLens(lens, packet)} ")
            }
            if (index < packets.lastIndex) delay(BLE_GAP_MS)
        }
        "svc=0x${serviceId.toString(16)} sid=$syncId pkts=${packets.size} bytes=${payload.size} -> ${results.toString().trim()}"
    }

    @SuppressLint("MissingPermission")
    private fun writeToLens(lens: Lens, packet: ByteArray): String {
        val gatt = lens.gatt ?: return "no-gatt"
        val char = lens.writeChar ?: return "no-char"
        return try {
            val result = gatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            if (result == BluetoothStatusCodes.SUCCESS) "ok" else "err$result"
        } catch (throwable: Throwable) {
            "ex"
        }
    }

    private fun writeReport(report: String): File {
        val dir = File(context.getExternalFilesDir(null), "g2-hud-text").apply { mkdirs() }
        val file = File(dir, "g2-hud-text-${FILE_FORMAT.format(Date())}.txt")
        file.writeText(report)
        return file
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
        services.asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String = name ?: address ?: "(unnamed G2)"

    companion object {
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val AUTH_TIMEOUT_MS = 6_000L
        private const val STEP_DELAY_MS = 200L
        private const val BLE_GAP_MS = 12L
        private const val HEARTBEAT_TICK_MS = 5_000L
        private const val PAGE_REFRESH_MS = 12_000L
        private const val TEXT_CONTAINER_ID = 1

        private val COMMAND_WRITE_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5401")
        private val COMMAND_NOTIFY_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5402")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        private val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
