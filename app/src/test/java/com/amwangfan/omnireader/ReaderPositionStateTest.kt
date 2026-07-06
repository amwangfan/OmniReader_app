package com.amwangfan.omnireader

import com.amwangfan.omnireader.reader.EpubBlock
import com.amwangfan.omnireader.reader.EpubChapter
import com.amwangfan.omnireader.reader.LocatorResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionStateTest {
    @Test fun latestVisiblePosition_isAvailableBeforeDebouncedPersistence() {
        val blocks = (0..3).map { EpubBlock(it, "p", "block-$it", "hash-$it") }
        val reader = ReaderUiState("local", "Book", listOf(EpubChapter("c", "C", blocks)), "remote")
            .withVisiblePosition(3, 7)
        val locator = LocatorResolver.locatorFor(reader.chapters, "rev", reader.currentChapterIndex, reader.initialBlockIndex, reader.initialScrollOffset)
        assertEquals(3, locator.blockIndex)
        assertEquals(7, locator.charOffset)
        assertTrue(reader.positionChanged)
    }
}
