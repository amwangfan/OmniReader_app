package com.amwangfan.omnireader.reader

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId

class ReadingTimeTracker(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private var running = false
    private var lastElapsedMillis = 0L
    private var cursorWallMillis = 0L
    private val remainderMillisByDate = mutableMapOf<String, Long>()

    fun start() {
        if (running) return
        running = true
        lastElapsedMillis = elapsedRealtime()
        cursorWallMillis = wallTimeMillis()
    }

    fun checkpoint(): Map<String, Long> {
        if (!running) return emptyMap()
        val nowElapsed = elapsedRealtime()
        var remaining = (nowElapsed - lastElapsedMillis).coerceAtLeast(0)
        if (remaining == 0L) return emptyMap()
        lastElapsedMillis = nowElapsed
        val result = linkedMapOf<String, Long>()
        while (remaining > 0) {
            val current = Instant.ofEpochMilli(cursorWallMillis).atZone(zoneId)
            val nextMidnight = current.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val segmentMillis = minOf(remaining, (nextMidnight - cursorWallMillis).coerceAtLeast(1))
            val date = current.toLocalDate().toString()
            val accumulated = remainderMillisByDate.getOrDefault(date, 0) + segmentMillis
            val seconds = accumulated / 1000
            remainderMillisByDate[date] = accumulated % 1000
            if (seconds > 0) result[date] = result.getOrDefault(date, 0) + seconds
            cursorWallMillis += segmentMillis
            remaining -= segmentMillis
        }
        return result
    }

    fun stop(): Map<String, Long> {
        if (!running) return emptyMap()
        val result = checkpoint()
        running = false
        return result
    }
}
