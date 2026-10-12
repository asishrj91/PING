package com.ping.app

const val HEARTBEAT_OVERDUE_MS = 40_000L

fun heartbeatOverdue(timestamp: Long, now: Long): Boolean =
    timestamp > 0L && now - timestamp >= HEARTBEAT_OVERDUE_MS

fun heartbeatStatus(timestamp: Long, now: Long, databaseConnected: Boolean): String = when {
    timestamp <= 0L -> "WAITING"
    heartbeatOverdue(timestamp, now) -> compactAge(timestamp, now)
    !databaseConnected -> "WAITING"
    else -> "LIVE"
}

fun compactAge(timestamp: Long, now: Long): String {
    val seconds = (now - timestamp).coerceAtLeast(0L) / 1000L
    return when {
        seconds < 60 -> "${seconds}s AGO"
        seconds < 3600 -> "${seconds / 60}m AGO"
        seconds < 360000 -> "${seconds / 3600}h AGO"
        seconds < 86400000 -> "${seconds / 86400}d AGO"
        else -> "999d+ AGO"
    }
}

fun compactMovementAge(timestamp: Long?, now: Long): String = when {
    timestamp == null -> "UNAVAILABLE"
    timestamp == 0L -> "NONE YET"
    else -> compactAge(timestamp, now)
}

fun trackingAge(timestamp: Long, now: Long): String {
    if (timestamp <= 0L) return "WAITING"
    val seconds = ((now - timestamp).coerceAtLeast(0L)) / 1000L
    return when {
        seconds < 5L -> "JUST NOW"
        seconds < 60L -> "${seconds}s AGO"
        seconds < 3600L -> "${seconds / 60L}m AGO"
        else -> "${seconds / 3600L}h AGO"
    }
}

fun movementAge(timestamp: Long?, now: Long): String = when {
    timestamp == null -> "NOT REPORTED"
    timestamp == 0L -> "NONE DETECTED YET"
    else -> trackingAge(timestamp, now)
}
