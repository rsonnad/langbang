package com.sponic.langbangtrans.g2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class G2DiscoveryClient(
    private val context: Context,
    private val config: G2Config,
) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun discoverAndSave(): String {
        val startedAt = Date()
        val devices = scanForDevices()
        val report = buildString {
            appendLine("LangBangTrans G2 BLE Discovery")
            appendLine("timestamp=${ISO_FORMAT.format(startedAt)}")
            appendLine("nameRegex=${config.nameRegex}")
            appendLine("candidateCount=${devices.size}")
            appendLine()
            if (devices.isEmpty()) {
                appendLine("No Even G2 devices found. Make sure the official Even app is closed and the glasses are awake.")
            }
            for ((index, device) in devices.withIndex()) {
                BridgeStatusBus.set("G2 discovery", "Connecting ${index + 1}/${devices.size}: ${device.safeName()}")
                appendLine(discoverDevice(device))
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
                    BridgeStatusBus.set("G2 discovery", "Found ${found.size}: ${found.values.joinToString { it.safeName() }}")
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
    private suspend fun discoverDevice(device: BluetoothDevice): String =
        withTimeout(CONNECT_TIMEOUT_MS) {
            val services = CompletableDeferred<String>()
            var gattRef: BluetoothGatt? = null
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        gattRef = gatt
                        gatt.discoverServices()
                        return
                    }
                    if (!services.isCompleted) {
                        services.complete(
                            deviceHeader(device) +
                                "connectStatus=$status newState=$newState\n"
                        )
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val text = buildString {
                        append(deviceHeader(device))
                        appendLine("serviceDiscoveryStatus=$status")
                        appendLine("serviceCount=${gatt.services.size}")
                        for (service in gatt.services) {
                            appendLine("service ${service.uuid} type=${service.type}")
                            for (characteristic in service.characteristics) {
                                appendLine(
                                    "  char ${characteristic.uuid} " +
                                        "properties=${characteristic.properties.propertyNames()} " +
                                        "permissions=${characteristic.permissions}"
                                )
                                for (descriptor in characteristic.descriptors) {
                                    appendLine(
                                        "    desc ${descriptor.uuid} permissions=${descriptor.permissions}"
                                    )
                                }
                            }
                        }
                    }
                    if (!services.isCompleted) services.complete(text)
                }
            }

            try {
                gattRef = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                services.await()
            } finally {
                delay(250)
                runCatching { gattRef?.disconnect() }
                runCatching { gattRef?.close() }
            }
        }

    private fun writeReport(report: String): File {
        val dir = File(context.getExternalFilesDir(null), "g2-discovery").apply { mkdirs() }
        val file = File(dir, "g2-discovery-${FILE_FORMAT.format(Date())}.txt")
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
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        private val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}

