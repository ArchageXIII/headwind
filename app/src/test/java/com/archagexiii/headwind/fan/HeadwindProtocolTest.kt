package com.archagexiii.headwind.fan

import org.junit.Assert.*
import org.junit.Test

class HeadwindProtocolTest {
    @Test fun powerToggleStartsAtTwentyAndUsesPendingIntent() {
        val stopped = FanUiState(Connection.READY, FanStatus(1, 0))
        assertEquals(20, stopped.toggledPowerSpeed)
        assertEquals(0, stopped.copy(requestedSpeed = 20).toggledPowerSpeed)
        val running = FanUiState(Connection.READY, FanStatus(4, 60))
        assertEquals(0, running.toggledPowerSpeed)
        assertEquals(20, running.copy(requestedSpeed = 0).toggledPowerSpeed)
        assertEquals(0, FanUiState(Connection.READY, FanStatus(2, 0)).toggledPowerSpeed)
        assertEquals(20, FanUiState(Connection.READY, FanStatus(5, 0)).toggledPowerSpeed)
    }

    @Test fun discoveryRecognisesOtherHeadwindsButNotUnrelatedDevices() {
        assertTrue(HeadwindProtocol.isHeadwindName("HEADWIND 1234"))
        assertTrue(HeadwindProtocol.isHeadwindName("headwind ABCD"))
        assertFalse(HeadwindProtocol.isHeadwindName("KICKR CORE"))
        assertFalse(HeadwindProtocol.isHeadwindName(null))
    }

    @Test fun reconnectRequiresTheSelectedAddressEvenWithIdenticalNames() {
        val selected = FanDevice("HEADWIND", "AA:BB:CC:DD:EE:01")
        assertTrue(isSelectedFan("aa:bb:cc:dd:ee:01", selected))
        assertFalse(isSelectedFan("AA:BB:CC:DD:EE:02", selected))
        assertFalse(isSelectedFan(selected.address, null))
    }

    @Test fun sixStepsStartAtMinimumAndStopAtMaximum() {
        var speed = 0
        val visited = (1..6).map { speed = HeadwindProtocol.increase(speed); speed }
        assertEquals(listOf(10, 20, 40, 60, 80, 100), visited)
        assertEquals(100, HeadwindProtocol.increase(speed))
        assertEquals(listOf(80, 60, 40, 20, 10, 0), (1..6).map { speed = HeadwindProtocol.decrease(speed); speed })
        assertEquals(0, HeadwindProtocol.decrease(0))
    }

    @Test fun externalSpeedsMoveToAdjacentPreset() {
        assertEquals(40, HeadwindProtocol.increase(25))
        assertEquals(20, HeadwindProtocol.decrease(25))
        assertEquals(0, HeadwindProtocol.decrease(2))
        assertEquals(10, HeadwindProtocol.increase(1))
    }

    @Test fun capturesDecodeStandbyManualAndSensorModes() {
        assertEquals(FanStatus(1, 0), HeadwindProtocol.status(byteArrayOf(0xfd.toByte(), 1, 0, 1)))
        assertEquals(FanStatus(4, 20), HeadwindProtocol.status(byteArrayOf(0xfd.toByte(), 1, 20, 4)))
        assertFalse(FanStatus(2, 20).matches(20))
        assertTrue(FanStatus(5, 0).matches(0))
        assertFalse(FanStatus(4, 20).matches(0))
        assertNull(HeadwindProtocol.status(byteArrayOf(0xfd.toByte(), 1, 101, 4)))
        assertNull(HeadwindProtocol.status(byteArrayOf(0xfd.toByte(), 1)))
        assertNull(HeadwindProtocol.status(byteArrayOf(0xfe.toByte(), 2, 1, 20)))
    }

    @Test fun realAcknowledgementsAndRejectionsAreDistinct() {
        val speed = HeadwindProtocol.speed(20)
        assertEquals(true, HeadwindProtocol.acknowledgement(byteArrayOf(0xfe.toByte(), 2, 1, 20), speed))
        assertEquals(false, HeadwindProtocol.acknowledgement(byteArrayOf(0xfe.toByte(), 2, 4), speed))
        assertNull(HeadwindProtocol.acknowledgement(byteArrayOf(0xfe.toByte(), 2, 1, 40), speed))
        assertNull(HeadwindProtocol.acknowledgement(byteArrayOf(0xfd.toByte(), 1, 20, 4), speed))
        assertArrayEquals(byteArrayOf(4, 1, 0, 0), HeadwindProtocol.standby())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroUsesStandbyInsteadOfSpeedCommand() { HeadwindProtocol.speed(0) }
}
