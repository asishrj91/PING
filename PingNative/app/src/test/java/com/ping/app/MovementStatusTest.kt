package com.ping.app

import org.junit.Assert.*
import org.junit.Test

class MovementStatusTest {
    @Test fun needsTwoDistinctMovementUpdates() {
        val first = MovementStatus().observe(100_000L)
        assertFalse(first.isMoving(100_000L, true))
        assertFalse(first.observe(100_000L).isMoving(110_000L, true))
        assertTrue(first.observe(110_000L).isMoving(110_000L, true))
    }

    @Test fun isolatedUpdatesDoNotStartMoving() {
        val first = MovementStatus().observe(100_000L)
        assertFalse(first.observe(130_001L).isMoving(130_001L, true))
        assertTrue(first.observe(130_000L).isMoving(130_000L, true))
    }

    @Test fun stopsAfterThirtySecondsEvenWithRepeatedHeartbeats() {
        val moving = MovementStatus().observe(100_000L).observe(110_000L)
        assertTrue(moving.isMoving(139_999L, true))
        assertFalse(moving.observe(110_000L).isMoving(140_000L, true))
        assertTrue(moving.observe(135_000L).isMoving(140_000L, true))
    }

    @Test fun staleConnectionAndOldSnapshotsDoNotClaimMovement() {
        val moving = MovementStatus().observe(100_000L).observe(110_000L)
        assertFalse(moving.isMoving(110_000L, false))
        assertEquals(moving, moving.observe(90_000L))
        assertFalse(moving.isMoving(109_000L, true))
        assertFalse(moving.observe(null).isMoving(110_000L, true))
        assertFalse(moving.observe(0L).isMoving(110_000L, true))
    }
}
