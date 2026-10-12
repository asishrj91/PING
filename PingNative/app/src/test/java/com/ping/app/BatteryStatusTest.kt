package com.ping.app
import org.junit.Assert.*
import org.junit.Test
class BatteryStatusTest {
    @Test fun convertsAndRejectsUnavailableReadings() {
        assertEquals(72, batteryPercent(72, 100))
        assertEquals(50, batteryPercent(100, 200))
        assertEquals(0, batteryPercent(0, 100))
        assertEquals(100, batteryPercent(100, 100))
        assertNull(batteryPercent(-1, 100))
        assertNull(batteryPercent(10, 0))
        assertNull(batteryPercent(101, 100))
    }
}
