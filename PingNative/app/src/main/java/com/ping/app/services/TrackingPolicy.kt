package com.ping.app.services

/** Decisions shared by location callbacks and the independent heartbeat timer. */
object TrackingPolicy {
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val LOCATION_INTERVAL_MS = 10_000L
    const val MOVEMENT_METERS = 5f

    fun shouldPublishLocation(distanceMeters: Float?): Boolean {
        if (distanceMeters == null) return true // Always publish the first fix.
        // Accuracy is reported separately; it must not silently raise the chosen threshold.
        return distanceMeters.isFinite() && distanceMeters > MOVEMENT_METERS
    }
}
