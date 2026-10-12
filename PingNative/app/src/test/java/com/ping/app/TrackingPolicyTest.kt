package com.ping.app

import com.ping.app.services.TrackingPolicy
import org.junit.Assert.*
import org.junit.Test

class TrackingPolicyTest {
    @Test fun firstFixIsAlwaysPublished() {
        assertTrue(TrackingPolicy.shouldPublishLocation(null))
    }

    @Test fun stationaryAndSubThresholdChangesAreNotPublished() {
        for (distance in listOf(0f, 1f, 4.9f, 5f)) {
            assertFalse(TrackingPolicy.shouldPublishLocation(distance))
        }
    }

    @Test fun movementBeyondFiveMetresIsPublishedWithoutAnAccuracyGate() {
        for (distance in listOf(5.1f, 6f, 12f, 100f)) {
            assertTrue(TrackingPolicy.shouldPublishLocation(distance))
        }
    }

    @Test fun invalidDistancesAreNotPublished() {
        for (distance in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f)) {
            assertFalse(TrackingPolicy.shouldPublishLocation(distance))
        }
    }

    @Test fun heartbeatDoesNotChangeMovementAge() {
        val movementTime = 1_000_000L
        val now = movementTime + 120_000L
        assertEquals("JUST NOW", trackingAge(now, now))
        assertEquals("2m AGO", movementAge(movementTime, now))
    }

    @Test fun missingMovementIsNotClaimedToBeRecent() {
        assertEquals("NOT REPORTED", movementAge(null, 1_000_000L))
        assertEquals("NONE DETECTED YET", movementAge(0L, 1_000_000L))
        assertEquals("WAITING", trackingAge(0L, 1_000_000L))
        assertEquals("JUST NOW", trackingAge(1_000_100L, 1_000_000L))
    }
}
