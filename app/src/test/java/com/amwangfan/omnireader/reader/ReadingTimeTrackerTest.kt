package com.amwangfan.omnireader.reader

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTrackerTest {
    @Test fun countsOnlyStartedMonotonicTimeAndCheckpointIsIncremental() {
        var millis = 1_000L
        val tracker = ReadingTimeTracker({ millis }, { LocalDate.of(2026, 7, 6) })
        assertEquals(emptyMap<String, Long>(), tracker.checkpoint())
        tracker.start(); millis += 2_500
        assertEquals(mapOf("2026-07-06" to 2L), tracker.checkpoint())
        millis += 1_000
        assertEquals(mapOf("2026-07-06" to 1L), tracker.stop())
        assertEquals(emptyMap<String, Long>(), tracker.stop())
    }

    @Test fun splitsElapsedSecondsAcrossLocalDateBoundary() {
        var millis = 0L
        var date = LocalDate.of(2026, 7, 5)
        val tracker = ReadingTimeTracker({ millis }, { date })
        tracker.start(); millis = 2_000; date = date.plusDays(1)
        assertEquals(mapOf("2026-07-05" to 2L), tracker.checkpoint())
        millis = 5_000
        assertEquals(mapOf("2026-07-06" to 3L), tracker.stop())
    }
}
