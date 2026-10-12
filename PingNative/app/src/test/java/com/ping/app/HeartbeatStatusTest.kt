package com.ping.app

import org.junit.Assert.*
import org.junit.Test

class HeartbeatStatusTest {
    private val received = 1_000_000L

    @Test fun compactAgesStayBoundedForLongSessions() {
        assertEquals("99h AGO", compactAge(received, received + 99L * 3600_000))
        assertEquals("4d AGO", compactAge(received, received + 100L * 3600_000))
        assertEquals("999d+ AGO", compactAge(received, received + 1000L * 86400_000))
        assertEquals("NONE YET", compactMovementAge(0L, received))
        assertEquals("UNAVAILABLE", compactMovementAge(null, received))
    }

    @Test fun normalIntervalHasNoAgeCounter() {
        assertEquals("LIVE", heartbeatStatus(received, received + 30_000L, true))
        assertEquals("LIVE", heartbeatStatus(received, received + 39_999L, true))
        assertFalse(heartbeatOverdue(received, received + 39_999L))
    }

    @Test fun overdueCounterStartsAtFortySecondsAndKeepsCounting() {
        assertTrue(heartbeatOverdue(received, received + 40_000L))
        assertEquals("40s AGO", heartbeatStatus(received, received + 40_000L, true))
        assertEquals("50s AGO", heartbeatStatus(received, received + 50_000L, true))
        assertEquals("2m AGO", heartbeatStatus(received, received + 120_000L, false))
    }

    @Test fun missingAndDisconnectedHeartbeatsAreNotShownLive() {
        assertEquals("WAITING", heartbeatStatus(0L, received, true))
        assertEquals("WAITING", heartbeatStatus(received, received + 10_000L, false))
    }

    @Test fun newlyReceivedHeartbeatClearsOverdueStatus() {
        val now = received + 50_000L
        assertEquals("LIVE", heartbeatStatus(now, now, true))
        assertFalse(heartbeatOverdue(now, now))
    }
}
