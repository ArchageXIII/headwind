package com.archagexiii.headwind.fan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Foreground-only remote. Every GATT operation and callback is serialized on the main thread. */
@SuppressLint("MissingPermission") // All entry points check permission; revocation is also caught.
class HeadwindController(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val preferences = app.getSharedPreferences("fan", Context.MODE_PRIVATE)
    private var active = false
    private var stopping = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var operation: Operation? = null
    private var transportDone = false
    private var commandAcknowledged = false
    private var believedMode: Int? = null
    private var believedSpeed: Int? = null
    private var receiverRegistered = false
    private var lastNotification = 0L
    private var discoveryRequested = false
    private var pendingSelection: FanDevice? = null
    private var scanCallback: ScanCallback? = null

    // Existing installations already stored the verified address. Preserve it on upgrade.
    var selectedFan by mutableStateOf(preferences.getString("address", null)?.let {
        if (BluetoothAdapter.checkBluetoothAddress(it)) FanDevice(preferences.getString("name", null) ?: "HEADWIND", it) else null
    })
        private set
    var nearbyFans by mutableStateOf(emptyList<FanDevice>())
        private set
    var discovering by mutableStateOf(false)
        private set
    var discoveryMessage by mutableStateOf("Search for nearby HEADWIND fans")
        private set
    val targetName: String get() = (pendingSelection ?: selectedFan)?.name ?: "HEADWIND"

    var state by mutableStateOf(FanUiState())
        private set

    private data class Operation(val kind: Kind, val bytes: ByteArray? = null)
    private enum class Kind { SUBSCRIBE, READ, WRITE }

    private val deadline = Runnable { fail("Fan did not respond. Reconnecting…") }
    private val scanDeadline = Runnable {
        if (discovering) {
            stopScan()
            discoveryMessage = if (nearbyFans.isEmpty()) "No fans found. Check power and close other fan apps, then scan again." else "Choose a fan to remember it"
        } else fail("$targetName not found. Retrying…")
    }
    private val retry = Runnable { if (active) resumeConnection() }
    private val confirmationDeadline = Runnable { fail("Speed was not confirmed. Reconnecting…") }
    private val stopDeadline = Runnable { finishStop() }
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!active || state.connection != Connection.READY) return
            if (android.os.SystemClock.elapsedRealtime() - lastNotification > 12_000) {
                fail("Fan connection lost. Reconnecting…")
            } else handler.postDelayed(this, 4_000)
        }
    }

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active) return
            if (stopping) return finishStop()
            releaseConnection()
            resumeConnection()
        }
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        stopping = false
        handler.removeCallbacks(stopDeadline)
        if (!active) {
            active = true
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            // These protected broadcasts originate in the system Bluetooth service.
            ContextCompat.registerReceiver(app, radioReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        if (!scanning) resumeConnection()
    }

    fun stop() {
        // Finish an already requested mode/speed pair if Home is pressed between
        // its two writes. Do not scan, reconnect, or accept new requests in this grace period.
        if (state.requestedSpeed != null && gatt != null) {
            stopping = true
            handler.removeCallbacks(retry)
            handler.postDelayed(stopDeadline, 2_500)
        } else finishStop()
    }

    private fun finishStop() {
        active = false
        stopping = false
        releaseConnection()
        if (receiverRegistered) app.unregisterReceiver(radioReceiver)
        receiverRegistered = false
        state = FanUiState(connection = Connection.INACTIVE, message = "Disconnected")
    }

    fun retryNow() {
        if (gatt != null || scanning) return
        handler.removeCallbacks(retry)
        if (active) connect()
    }

    private fun resumeConnection() {
        if (discoveryRequested) discoverFans() else connect()
    }

    fun discoverFans() {
        discoveryRequested = true
        if (!active || discovering) return
        stopScan()
        nearbyFans = emptyList()
        if (!checkEnvironment()) {
            discoveryMessage = when (state.connection) {
                Connection.PERMISSION -> "Nearby devices permission is needed. Tap Scan again to allow it."
                Connection.BLUETOOTH_OFF -> "Bluetooth is off. Tap Scan again to turn it on."
                Connection.LOCATION_OFF -> "Location must be enabled for scanning on this Android version. Tap Scan again."
                else -> state.message
            }
            return
        }
        if (gatt == null) state = FanUiState(Connection.NEEDS_SELECTION, message = "Choose a fan, or close settings to reconnect")
        try {
            manager.getConnectedDevices(BluetoothProfile.GATT).forEach { device ->
                device.name?.takeIf(HeadwindProtocol::isHeadwindName)?.let { addNearby(FanDevice(it, device.address)) }
            }
            discoveryMessage = "Searching for nearby HEADWIND fans…"
            beginScan(forDiscovery = true)
        } catch (_: SecurityException) {
            discoveryMessage = "Allow Nearby devices to search for fans"
        } catch (_: IllegalStateException) {
            discoveryMessage = "Bluetooth is unavailable. Try scanning again."
        }
    }

    fun closeDiscovery() {
        discoveryRequested = false
        if (discovering) stopScan()
        if (active) connect()
    }

    fun selectFan(address: String) {
        if (!active || state.requestedSpeed != null) return
        val candidate = nearbyFans.firstOrNull { it.address == address } ?: return
        releaseConnection()
        discoveryRequested = false
        pendingSelection = candidate
        connect()
    }

    fun forgetFan() {
        if (state.requestedSpeed != null) return
        releaseConnection()
        pendingSelection = null
        selectedFan = null
        preferences.edit().remove("address").remove("name").apply()
        state = FanUiState(Connection.NEEDS_SELECTION, message = "Choose a fan in settings")
        discoverFans()
    }

    private fun addNearby(fan: FanDevice) {
        nearbyFans = (nearbyFans.filterNot { it.address == fan.address } + fan).sortedWith(compareBy({ it.name }, { it.address }))
    }

    fun increase() {
        if (!state.canControl) return
        val current = state.requestedSpeed ?: state.status!!.let { if (it.isStopped) 0 else it.speed }
        request(HeadwindProtocol.increase(current))
    }

    fun decrease() {
        if (!state.canControl) return
        val current = state.requestedSpeed ?: state.status!!.let { if (it.isStopped) 0 else it.speed }
        request(HeadwindProtocol.decrease(current))
    }

    fun off() {
        if (state.canControl) request(0)
    }

    private fun request(target: Int) {
        if (stopping) return
        // A single latest target coalesces rapid presses. OFF replaces every unsent speed.
        state = state.copy(requestedSpeed = target, message = if (target == 0) "Stopping…" else "Setting $target%…")
        handler.removeCallbacks(confirmationDeadline)
        handler.postDelayed(confirmationDeadline, 8_000)
        driveTarget()
    }

    private fun checkEnvironment(): Boolean {
        if (!hasPermissions()) {
            state = FanUiState(Connection.PERMISSION, message = "Tap + to allow Nearby devices")
            return false
        }
        val bluetooth = adapter
        if (bluetooth == null) {
            state = FanUiState(Connection.UNAVAILABLE, message = "Bluetooth LE is unavailable")
            return false
        }
        if (!bluetooth.isEnabled) {
            state = FanUiState(Connection.BLUETOOTH_OFF, message = "Tap + to turn on Bluetooth")
            return false
        }
        if (Build.VERSION.SDK_INT < 31) {
            val location = app.getSystemService(LocationManager::class.java)
            val enabled = if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled else
                location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!enabled) {
                state = FanUiState(Connection.LOCATION_OFF, message = "Tap + to enable Location for Bluetooth scanning")
                return false
            }
        }
        return true
    }

    private fun connect() {
        if (!active || stopping || gatt != null || scanning || discoveryRequested) return
        handler.removeCallbacks(retry)
        if (!checkEnvironment()) return
        if (pendingSelection == null && selectedFan == null) {
            state = FanUiState(Connection.NEEDS_SELECTION, message = "Tap the cog to choose your fan")
            return
        }
        try {
            // A remembered address is the identity; another fan with the same name is never substituted.
            val connected = manager.getConnectedDevices(BluetoothProfile.GATT).firstOrNull(::isTarget)
            if (connected != null) {
                open(connected)
                return
            }
            state = FanUiState(Connection.SEARCHING, message = "Finding $targetName…")
            beginScan(forDiscovery = false)
        } catch (_: SecurityException) {
            releaseConnection()
            state = FanUiState(Connection.PERMISSION, message = "Tap + to allow Nearby devices")
        } catch (error: IllegalStateException) {
            fail("Bluetooth is unavailable. Retrying…")
        }
    }

    private fun isTarget(device: BluetoothDevice): Boolean =
        isSelectedFan(device.address, pendingSelection ?: selectedFan)

    private fun beginScan(forDiscovery: Boolean) {
        val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("Scanner unavailable")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post {
                    if (!active || !scanning || scanCallback !== this) return@post
                    try {
                        val name = result.scanRecord?.deviceName ?: result.device.name
                        if (forDiscovery) {
                            if (HeadwindProtocol.isHeadwindName(name)) addNearby(FanDevice(name!!, result.device.address))
                        } else if (isTarget(result.device)) {
                            stopScan()
                            open(result.device)
                        }
                    } catch (_: SecurityException) { fail("Bluetooth permission is needed") }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (scanCallback !== this) return@post
                    if (forDiscovery) {
                        stopScan()
                        discoveryMessage = "Scan failed ($errorCode). Wait a moment and scan again."
                    } else fail("Bluetooth scan failed ($errorCode). Retrying…")
                }
            }
        }
        scanCallback = callback
        scanning = true
        discovering = forDiscovery
        val filters = if (forDiscovery) emptyList() else listOf(
            ScanFilter.Builder().setDeviceAddress((pendingSelection ?: selectedFan)!!.address).build(),
        )
        try {
            scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            handler.postDelayed(scanDeadline, 12_000)
        } catch (error: RuntimeException) {
            stopScan()
            throw error
        }
    }

    private fun open(device: BluetoothDevice) {
        state = FanUiState(Connection.CONNECTING, message = "Connecting to $targetName…")
        Log.i(TAG, "Connecting to target fan")
        gatt = device.connectGatt(app, false, callbacks, BluetoothDevice.TRANSPORT_LE)
        armDeadline(15_000)
    }

    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) = dispatch(client) {
            Log.i(TAG, "Connection status=$status state=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail("Connection lost. Retrying…")
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                armDeadline()
                if (!client.discoverServices()) fail("Cannot inspect fan. Retrying…")
            }
        }

        override fun onServicesDiscovered(client: BluetoothGatt, status: Int) = dispatch(client) {
            if (status != BluetoothGatt.GATT_SUCCESS) return@dispatch fail("Cannot read fan services. Retrying…")
            val char = client.getService(HeadwindProtocol.service)?.getCharacteristic(HeadwindProtocol.command)
            if (char == null || char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                return@dispatch fail("Compatible HEADWIND control service not found")
            }
            characteristic = char
            val descriptor = char.getDescriptor(HeadwindProtocol.clientConfig)
                ?: return@dispatch fail("Fan notifications unavailable")
            if (!client.setCharacteristicNotification(char, true)) return@dispatch fail("Cannot listen to fan")
            operation = Operation(Kind.SUBSCRIBE)
            armDeadline()
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                client.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                client.writeDescriptor(descriptor)
            }
            if (!accepted) fail("Cannot subscribe to fan. Retrying…")
        }

        override fun onDescriptorWrite(client: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(client) {
            if (operation?.kind != Kind.SUBSCRIBE) return@dispatch
            if (status != BluetoothGatt.GATT_SUCCESS) return@dispatch fail("Fan subscription failed")
            operation = Operation(Kind.READ)
            armDeadline()
            if (!client.readCharacteristic(characteristic!!)) fail("Cannot read fan status")
        }

        override fun onCharacteristicRead(client: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            dispatch(client) { readComplete(value, status) }

        @Deprecated("Used on Android 12 and earlier")
        override fun onCharacteristicRead(client: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION") val value = char.value?.clone() ?: byteArrayOf()
                dispatch(client) { readComplete(value, status) }
            }
        }

        override fun onCharacteristicChanged(client: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) =
            dispatch(client) { receive(value) }

        @Deprecated("Used on Android 12 and earlier")
        override fun onCharacteristicChanged(client: BluetoothGatt, char: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION") val value = char.value?.clone() ?: byteArrayOf()
                dispatch(client) { receive(value) }
            }
        }

        override fun onCharacteristicWrite(client: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) = dispatch(client) {
            if (operation?.kind != Kind.WRITE) return@dispatch
            if (status != BluetoothGatt.GATT_SUCCESS) return@dispatch fail("Fan command failed. Reconnecting…")
            transportDone = true
            finishWriteIfReady()
        }
    }

    private fun dispatch(client: BluetoothGatt, action: () -> Unit) {
        handler.post {
            if (!active || client !== gatt) return@post
            try { action() } catch (_: SecurityException) { fail("Bluetooth permission is needed") }
        }
    }

    private fun readComplete(value: ByteArray, result: Int) {
        if (operation?.kind != Kind.READ) return
        operation = null
        handler.removeCallbacks(deadline)
        if (result != BluetoothGatt.GATT_SUCCESS) return fail("Cannot read fan status")
        val status = HeadwindProtocol.status(value)
        if (status == null && state.status == null) return fail("Unrecognised fan status. Retrying…")
        val device = gatt!!.device
        val verified = FanDevice(device.name ?: targetName, device.address)
        preferences.edit().putString("address", verified.address).putString("name", verified.name).apply()
        selectedFan = verified
        pendingSelection = null
        state = state.copy(connection = Connection.READY, message = "Connected · $targetName")
        receive(value)
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, 4_000)
    }

    private fun receive(value: ByteArray) {
        Log.d(TAG, "RX ${value.joinToString(" ") { "%02x".format(it) }}")
        val pending = operation
        if (pending?.kind == Kind.WRITE) {
            when (HeadwindProtocol.acknowledgement(value, pending.bytes!!)) {
                false -> return fail("Fan rejected the command. Reconnecting…")
                true -> {
                    commandAcknowledged = true
                    finishWriteIfReady()
                }
                null -> Unit
            }
        }
        val status = HeadwindProtocol.status(value) ?: return
        lastNotification = android.os.SystemClock.elapsedRealtime()
        val target = state.requestedSpeed
        // Cyclic status can lag a command; never let it overwrite a pending target.
        state = state.copy(status = status)
        if (target == null) {
            believedMode = status.mode
            believedSpeed = status.speed
        } else if (operation == null && status.matches(target)) {
            believedMode = status.mode
            believedSpeed = status.speed
            state = state.copy(requestedSpeed = null, message = "Connected · $targetName")
            handler.removeCallbacks(confirmationDeadline)
            if (stopping) finishStop()
        }
    }

    private fun driveTarget() {
        if (operation != null || !state.canControl) return
        val target = state.requestedSpeed ?: return
        when {
            target == 0 -> write(HeadwindProtocol.standby())
            believedMode != 4 -> write(HeadwindProtocol.manual())
            believedSpeed != target -> write(HeadwindProtocol.speed(target))
            else -> Unit // Wait for a status report that confirms the requested setting.
        }
    }

    private fun write(bytes: ByteArray) {
        val client = gatt ?: return
        val char = characteristic ?: return
        operation = Operation(Kind.WRITE, bytes)
        transportDone = false
        commandAcknowledged = false
        armDeadline()
        val type = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        Log.i(TAG, "TX ${bytes.joinToString(" ") { "%02x".format(it) }}")
        try {
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                client.writeCharacteristic(char, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                char.writeType = type
                @Suppress("DEPRECATION") client.writeCharacteristic(char)
            }
            if (!accepted) fail("Cannot send fan command. Reconnecting…")
        } catch (_: SecurityException) { fail("Bluetooth permission is needed") }
    }

    private fun finishWriteIfReady() {
        val current = operation ?: return
        if (!transportDone || !commandAcknowledged || current.kind != Kind.WRITE) return
        val bytes = current.bytes!!
        if (bytes[0].toInt() == 4) {
            believedMode = bytes[1].toInt()
            believedSpeed = null // Switching to manual resets this fan to 25%; set desired speed immediately.
        } else believedSpeed = bytes[1].toInt()
        operation = null
        handler.removeCallbacks(deadline)
        // Avoid repeatedly sending OFF while awaiting the cyclic status confirmation.
        if (state.requestedSpeed == 0 && believedMode == 1) return
        driveTarget()
    }

    private fun armDeadline(delay: Long = 5_000) {
        handler.removeCallbacks(deadline)
        handler.postDelayed(deadline, delay)
    }

    private fun stopScan() {
        val callback = scanCallback
        scanCallback = null
        if (scanning) {
            scanning = false
            try { if (callback != null) adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: SecurityException) { }
        }
        discovering = false
        handler.removeCallbacks(scanDeadline)
    }

    private fun releaseConnection() {
        handler.removeCallbacksAndMessages(null)
        stopScan()
        val old = gatt
        gatt = null
        try { old?.disconnect() } catch (_: SecurityException) { }
        try { old?.close() } catch (_: SecurityException) { }
        characteristic = null
        operation = null
        believedMode = null
        believedSpeed = null
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        if (stopping) return finishStop()
        releaseConnection()
        // Never replay a control request after a disconnect or after leaving the app.
        state = FanUiState(Connection.RETRYING, message = message)
        if (active) handler.postDelayed(retry, 3_000)
    }

    companion object {
        private const val TAG = "HeadwindBLE"
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
