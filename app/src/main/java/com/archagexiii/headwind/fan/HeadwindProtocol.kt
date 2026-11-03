package com.archagexiii.headwind.fan

import java.util.UUID

/** Wire format verified against a physical HEADWIND; see docs/bluetooth.md. */
object HeadwindProtocol {
    fun isHeadwindName(name: String?): Boolean = name?.trim()?.startsWith("HEADWIND", ignoreCase = true) == true
    val service: UUID = UUID.fromString("a026ee0c-0a7d-4ab3-97fa-f1500f9feb8b")
    val command: UUID = UUID.fromString("a026e038-0a7d-4ab3-97fa-f1500f9feb8b")
    val clientConfig: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val levels = listOf(10, 20, 40, 60, 80, 100)

    fun increase(speed: Int): Int = levels.firstOrNull { it > speed } ?: 100
    fun decrease(speed: Int): Int = levels.lastOrNull { it < speed } ?: 0
    fun manual(): ByteArray = byteArrayOf(4, 4, 0, 0)
    fun standby(): ByteArray = byteArrayOf(4, 1, 0, 0)
    fun speed(percent: Int): ByteArray {
        require(percent in 1..100)
        return byteArrayOf(2, percent.toByte(), 0, 0)
    }

    fun status(bytes: ByteArray): FanStatus? {
        if (bytes.size != 4 || bytes[0].unsigned() != 0xfd || bytes[1].unsigned() != 1) return null
        val mode = bytes[3].unsigned()
        val speed = bytes[2].unsigned()
        if (mode !in 1..5 || speed > 100) return null
        return FanStatus(mode, if (mode == 1 || mode == 5) 0 else speed)
    }

    /** Null: unrelated notification; false: rejection; true: command acknowledged. */
    fun acknowledgement(bytes: ByteArray, sent: ByteArray): Boolean? {
        if (bytes.size < 3 || bytes[0].unsigned() != 0xfe || bytes[1] != sent[0]) return null
        if (bytes[2].unsigned() != 1) return false
        if (bytes.size < 4 || bytes[3] != sent[1]) return null
        return true
    }

    private fun Byte.unsigned() = toInt() and 0xff
}

data class FanStatus(val mode: Int, val speed: Int) {
    val isStopped: Boolean get() = mode == 1 || mode == 5 || (mode == 4 && speed == 0)
    fun matches(target: Int): Boolean = if (target == 0) isStopped else mode == 4 && speed == target
}

data class FanDevice(val name: String, val address: String)

/** Reconnect by identity, never by a shared display name. */
fun isSelectedFan(address: String, selected: FanDevice?): Boolean =
    selected != null && address.equals(selected.address, ignoreCase = true)

enum class Connection { PERMISSION, BLUETOOTH_OFF, LOCATION_OFF, NEEDS_SELECTION, SEARCHING, CONNECTING, READY, RETRYING, INACTIVE, UNAVAILABLE }

data class FanUiState(
    val connection: Connection = Connection.INACTIVE,
    val status: FanStatus? = null,
    val requestedSpeed: Int? = null,
    val message: String = "Choose a HEADWIND in settings",
) {
    val canControl: Boolean get() = connection == Connection.READY && status != null
    val displayedSpeed: Int? get() = requestedSpeed ?: status?.speed
}
