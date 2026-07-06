package com.amwangfan.omnireader.reader

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTrackerTest {
    @Test fun countsOnlyStartedMonotonicTimeAndCheckpointIsIncremental() {
        var millis = 1_000L
        val base = at("2026-07-06T10:00:00+08:00")
        val tracker = ReadingTimeTracker({ millis }, { base + millis - 1_000 }, ZoneId.of("Asia/Shanghai"))
        assertEquals(emptyMap<String, Long>(), tracker.checkpoint())
        tracker.start(); millis += 2_500
        assertEquals(mapOf("2026-07-06" to 2L), tracker.checkpoint())
        millis += 1_000
        assertEquals(mapOf("2026-07-06" to 1L), tracker.stop())
        assertEquals(emptyMap<String, Long>(), tracker.stop())
    }

    @Test fun splitsElapsedSecondsAcrossLocalDateBoundary() {
        var millis = 0L
        val base = at("2026-07-05T23:59:58+08:00")
        val tracker = ReadingTimeTracker({ millis }, { base + millis }, ZoneId.of("Asia/Shanghai"))
        tracker.start(); millis = 2_000
        assertEquals(mapOf("2026-07-05" to 2L), tracker.checkpoint())
        millis = 5_000
        assertEquals(mapOf("2026-07-06" to 3L), tracker.stop())
    }

    private fun at(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
}
