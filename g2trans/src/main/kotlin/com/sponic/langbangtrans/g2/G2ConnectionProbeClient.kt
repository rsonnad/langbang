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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class G2ConnectionProbeClient(
    private val context: Context,
    private val config: G2Config,
) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun probeAndSave(): String {
        val startedAt = Date()
        val writeUuid = config.writeCharUuid.uuidOrDefault(COMMAND_WRITE_UUID)
        val notifyUuid = config.notifyCharUuid.uuidOrDefault(COMMAND_NOTIFY_UUID)
        val devices = scanForDevices()
        val report = buildString {
            appendLine("LangBangTrans G2 BLE Connection Probe")
            appendLine("timestamp=${ISO_FORMAT.format(startedAt)}")
            appendLine("nameRegex=${config.nameRegex}")
            appendLine("writeChar=$writeUuid")
            appendLine("notifyChar=$notifyUuid")
            appendLine("audioNotifyChar=$AUDIO_NOTIFY_UUID")
            appendLine("candidateCount=${devices.size}")
            appendLine()
            if (devices.isEmpty()) {
                appendLine("No Even G2 devices found. Keep the glasses awake and close the official Even app.")
            }
            for ((index, device) in devices.withIndex()) {
                BridgeStatusBus.set("G2 connect probe", "Connecting ${index + 1}/${devices.size}: ${device.safeName()}")
                appendLine(probeDevice(device, writeUuid, notifyUuid))
                appendLine()
            }
        }
        val file = writeReport(report)
        val withPath = "$report\nsavedPath=${file.absolutePath}\n"
        BridgeStatusBus.discovery(withPath)
        return withPath
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDevices(): List<BluetoothDevice> = withTimeout(SCAN_TIMEOUT_MS + 1_000L) {
        suspendCancellableCoroutine { continuation ->
            val scanner = bluetoothManager.adapter?.bluetoothLeScanner
            if (scanner == null) {
                continuation.resumeWithException(IllegalStateException("Bluetooth LE scanner unavailable"))
                return@suspendCancellableCoroutine
            }
            val matcher = Regex(config.nameRegex, RegexOption.IGNORE_CASE)
            val found = linkedMapOf<String, BluetoothDevice>()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.scanRecord?.deviceName ?: result.device.safeName()
                    if (!matcher.containsMatchIn(name)) return
                    found[result.device.address] = result.device
                    BridgeStatusBus.set("G2 connect probe", "Found ${found.size}: ${found.values.joinToString { it.safeName() }}")
                }

                override fun onScanFailed(errorCode: Int) {
                    scanner.stopScan(this)
                    continuation.resumeWithException(IllegalStateException("BLE scan failed: $errorCode"))
                }
            }
            scanner.startScan(callback)
            continuation.invokeOnCancellation { scanner.stopScan(callback) }
            android.os.Handler(context.mainLooper).postDelayed({
                scanner.stopScan(callback)
                if (continuation.isActive) continuation.resume(found.values.toList())
            }, SCAN_TIMEOUT_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun probeDevice(
        device: BluetoothDevice,
        writeUuid: UUID,
        notifyUuid: UUID,
    ): String = withTimeout(CONNECT_TIMEOUT_MS) {
        val report = CompletableDeferred<String>()
        var gattRef: BluetoothGatt? = null
        val notificationCounts = linkedMapOf<UUID, Int>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    gattRef = gatt
                    gatt.discoverServices()
                    return
                }
                if (!report.isCompleted) {
                    report.complete(
                        deviceHeader(device) +
                            "connectStatus=$status newState=$newState\n"
                    )
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!report.isCompleted) {
                        report.complete(deviceHeader(device) + "serviceDiscoveryStatus=$status\n")
                    }
                    return
                }

                val write = gatt.findCharacteristic(writeUuid)
                val notify = gatt.findCharacteristic(notifyUuid)
                val audioNotify = gatt.findCharacteristic(AUDIO_NOTIFY_UUID)
                val notifyResult = notify?.let { gatt.enableNotifications(it) }
                val audioResult = audioNotify?.let { gatt.enableNotifications(it) }
                if (!report.isCompleted) {
                    report.complete(
                        buildString {
                            append(deviceHeader(device))
                            appendLine("serviceDiscoveryStatus=$status")
                            appendLine("serviceCount=${gatt.services.size}")
                            appendLine("writeFound=${write != null} ${write.describe()}")
                            appendLine("notifyFound=${notify != null} ${notify.describe()}")
                            appendLine("notifyEnable=$notifyResult")
                            appendLine("audioNotifyFound=${audioNotify != null} ${audioNotify.describe()}")
                            appendLine("audioNotifyEnable=$audioResult")
                            appendLine("willWriteHudPackets=false")
                        }
                    )
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                notificationCounts[characteristic.uuid] = (notificationCounts[characteristic.uuid] ?: 0) + 1
            }
        }

        try {
            gattRef = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            val base = report.await()
            delay(750)
            base + "notificationCounts=${notificationCounts.entries.joinToString { "${it.key}:${it.value}" }.ifBlank { "none" }}\n"
        } finally {
            runCatching { gattRef?.disconnect() }
            runCatching { gattRef?.close() }
        }
    }

    private fun writeReport(report: String): File {
        val dir = File(context.getExternalFilesDir(null), "g2-connect-probe").apply { mkdirs() }
        val file = File(dir, "g2-connect-probe-${FILE_FORMAT.format(Date())}.txt")
        file.writeText(report)
        return file
    }

    @SuppressLint("MissingPermission")
    private fun deviceHeader(device: BluetoothDevice): String = buildString {
        appendLine("deviceName=${device.safeName()}")
        appendLine("address=${device.address}")
        appendLine("bondState=${device.bondState}")
        appendLine("type=${device.type}")
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String = name ?: address ?: "(unnamed G2)"

    private fun String.uuidOrDefault(default: UUID): UUID =
        if (isBlank()) default else UUID.fromString(this)

    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
        services.asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }

    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.enableNotifications(characteristic: BluetoothGattCharacteristic): String {
        val localEnabled = setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        val descriptorWrite = if (cccd == null) {
            "missing-cccd"
        } else {
            when (val result = writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                BluetoothStatusCodes.SUCCESS -> "descriptor-write-started"
                else -> "descriptor-write-failed-$result"
            }
        }
        return "local=$localEnabled $descriptorWrite"
    }

    private fun BluetoothGattCharacteristic?.describe(): String =
        if (this == null) {
            ""
        } else {
            "uuid=$uuid service=${service.uuid} properties=${properties.propertyNames()}"
        }

    private fun Int.propertyNames(): String {
        val names = buildList {
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("broadcast")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("write-no-response")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("signed-write")
            if (this@propertyNames and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("extended-props")
        }
        return names.joinToString("|").ifBlank { "none" }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private val COMMAND_WRITE_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5401")
        private val COMMAND_NOTIFY_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5402")
        private val AUDIO_NOTIFY_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e6402")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        private val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
