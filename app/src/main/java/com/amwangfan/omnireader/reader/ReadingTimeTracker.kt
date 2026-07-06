package com.amwangfan.omnireader.reader

import android.os.SystemClock
import java.time.LocalDate

class ReadingTimeTracker(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val localDate: () -> LocalDate = LocalDate::now,
) {
    private var running = false
    private var lastMillis = 0L
    private var lastDate: LocalDate? = null

    fun start() {
        if (running) return
        running = true
        lastMillis = elapsedRealtime()
        lastDate = localDate()
    }

    fun checkpoint(): Map<String, Long> {
        if (!running) return emptyMap()
        val now = elapsedRealtime()
        val seconds = ((now - lastMillis).coerceAtLeast(0) / 1000)
        if (seconds == 0L) return emptyMap()
        val date = lastDate ?: localDate()
        lastMillis += seconds * 1000
        lastDate = localDate()
        return mapOf(date.toString() to seconds)
    }

    fun stop(): Map<String, Long> {
        if (!running) return emptyMap()
        val result = checkpoint()
        running = false
        lastDate = null
        return result
    }
}
