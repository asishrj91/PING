package com.ping.app

/** Distinct server movement timestamps, unaffected by repeated heartbeat snapshots. */
data class MovementStatus(val previous: Long = 0L, val latest: Long = 0L) {
    fun observe(timestamp: Long?): MovementStatus = when {
        timestamp == null || timestamp <= 0L -> MovementStatus()
        timestamp <= latest -> this
        else -> MovementStatus(previous = latest, latest = timestamp)
    }

    fun isMoving(now: Long, heartbeatFresh: Boolean): Boolean =
        heartbeatFresh && previous > 0L && latest - previous in 1L..30_000L &&
            now - latest in 0L until 30_000L
}
