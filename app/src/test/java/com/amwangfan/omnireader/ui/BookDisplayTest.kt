package com.amwangfan.omnireader.ui

import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.BookSource
import com.amwangfan.omnireader.data.BookSyncState
import com.amwangfan.omnireader.data.LocalBook
import org.junit.Assert.assertEquals
import org.junit.Test

class BookDisplayTest {
    @Test
    fun bookSyncLabel_describesImportState() {
        assertEquals("Pending upload", bookSyncLabel(localBook(BookSyncState.PENDING_UPLOAD)))
        assertEquals("Synced", bookSyncLabel(localBook(BookSyncState.SYNCED)))
        assertEquals("Local only", bookSyncLabel(localBook(BookSyncState.LOCAL_ONLY)))
    }

    @Test
    fun localBookForRemote_matchesRemoteIdentityInsteadOfLocalId() {
        val local = localBook(BookSyncState.SYNCED).copy(
            id = "local-checksum",
            remoteBookId = "remote-1",
        )
        val remote = BookDto(
            id = "remote-1",
            title = "Remote",
            format = "epub",
            fileSize = 1,
            checksum = "sum",
            createdAt = "now",
            updatedAt = "now",
        )

        assertEquals(local, localBookForRemote(remote, listOf(local)))
    }

    private fun localBook(state: BookSyncState) = LocalBook(
        id = "local-sum",
        title = "Imported",
        fileName = "imported.epub",
        fileSize = 1,
        checksum = "sum",
        downloadedAtEpochMillis = 1,
        source = BookSource.LOCAL_IMPORT,
        syncState = state,
    )
}
