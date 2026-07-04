package com.amwangfan.omnireader

import com.amwangfan.omnireader.data.BookSource
import com.amwangfan.omnireader.data.BookSyncState
import com.amwangfan.omnireader.data.LocalBook
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingUploadTest {
    @Test
    fun processPendingUploads_continuesAfterFailure() = runTest {
        val first = pendingBook("first")
        val second = pendingBook("second")
        val attempted = mutableListOf<String>()

        val failures = processPendingUploads(listOf(first, second)) { book ->
            attempted += book.id
            if (book.id == "first") error("offline")
        }

        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf("First"), failures)
    }

    private fun pendingBook(id: String) = LocalBook(
        id = id,
        title = id.replaceFirstChar(Char::uppercase),
        fileName = "$id.epub",
        fileSize = 1,
        checksum = id,
        downloadedAtEpochMillis = 1,
        source = BookSource.LOCAL_IMPORT,
        syncState = BookSyncState.PENDING_UPLOAD,
    )
}
