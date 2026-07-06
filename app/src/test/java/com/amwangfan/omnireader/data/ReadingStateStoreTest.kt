package com.amwangfan.omnireader.data

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStateStoreTest {
    @Test fun roundTrip_keepsPerBookPerDeviceState() {
        val dir = tempDir()
        val store = ReadingStateStore(dir)
        val record = ReadingStateRecord(locator(), mapOf("2026-07-06" to 45), dirty = true, lastServerUpdatedAt = "server-time")
        store.put("book", "device", record)
        assertEquals(record, ReadingStateStore(dir).get("book", "device"))
        assertTrue(File(dir, "reading_state.json").isFile)
        assertFalse(File(dir, "reading_state.json.tmp").exists())
    }

    @Test fun markClean_preservesTotalsAndStoresServerTimestamp() {
        val store = ReadingStateStore(tempDir())
        store.put("b", "d", ReadingStateRecord(locator(), mapOf("2026-07-06" to 2), true))
        store.markClean("b", "d", "accepted")
        assertFalse(store.get("b", "d")!!.dirty)
        assertEquals("accepted", store.get("b", "d")!!.lastServerUpdatedAt)
    }

    @Test fun corruptInput_isPreservedAndReturnsEmptyState() {
        val dir = tempDir()
        File(dir, "reading_state.json").writeText("not json")
        val store = ReadingStateStore(dir, nowMillis = { 123 })
        assertEquals(null, store.get("b", "d"))
        assertTrue(File(dir, "reading_state.json.corrupt-123").isFile)
    }

    private fun locator() = ReadingLocator(contentRevision="r", chapterHref="c", chapterIndex=0, blockIndex=0)
    private fun tempDir() = createTempDirectory("reading-state-").toFile().apply { deleteOnExit() }
}
