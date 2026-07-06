package com.amwangfan.omnireader.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBookIndexTest {
    @Test
    fun normalizeLegacyBooks_assignsServerIdentityAndSyncedState() {
        val legacy = Json.decodeFromString<LocalBookIndex>(
            """{"books":[{"id":"server-1","title":"Book","author":"","fileName":"server-1.epub","fileSize":42,"checksum":"sum","downloadedAtEpochMillis":3}]}""",
        )

        val normalized = legacy.normalized()

        assertEquals("server-1", normalized.books.single().remoteBookId)
        assertEquals(BookSource.SERVER_DOWNLOAD, normalized.books.single().source)
        assertEquals(BookSyncState.SYNCED, normalized.books.single().syncState)
    }

    @Test
    fun removeAndPendingUploads_useStableLocalId() {
        val pending = LocalBook(
            id = "local-sum",
            title = "Imported",
            fileName = "imported.epub",
            fileSize = 10,
            checksum = "sum",
            downloadedAtEpochMillis = 1,
            source = BookSource.LOCAL_IMPORT,
            syncState = BookSyncState.PENDING_UPLOAD,
        )
        val synced = LocalBook(
            id = "server-1",
            title = "Downloaded",
            fileName = "server-1.epub",
            fileSize = 20,
            checksum = "server-sum",
            downloadedAtEpochMillis = 2,
            remoteBookId = "server-1",
            syncState = BookSyncState.SYNCED,
        )
        val index = LocalBookIndex(listOf(pending, synced))

        assertEquals(listOf("local-sum"), index.pendingUploads().map { it.id })
        assertEquals(listOf("server-1"), index.remove("local-sum").books.map { it.id })
    }

    @Test
    fun upsert_replacesExistingBookAndSortsByTitle() {
        val first = LocalBook(
            id = "book_1",
            title = "Zulu",
            fileName = "book_1.epub",
            fileSize = 10,
            checksum = "old",
            downloadedAtEpochMillis = 1,
        )
        val second = LocalBook(
            id = "book_2",
            title = "Alpha",
            fileName = "book_2.epub",
            fileSize = 20,
            checksum = "new",
            downloadedAtEpochMillis = 2,
        )
        val updatedFirst = first.copy(title = "Beta", checksum = "updated")

        val index = LocalBookIndex()
            .upsert(first)
            .upsert(second)
            .upsert(updatedFirst)

        assertEquals(listOf("Alpha", "Beta"), index.books.map { it.title })
        assertEquals("updated", index.books.single { it.id == "book_1" }.checksum)
    }

    @Test
    fun localBookIndex_roundTripsJson() {
        val json = Json
        val index = LocalBookIndex(
            listOf(
                LocalBook(
                    id = "book_1",
                    title = "Book",
                    author = "Author",
                    fileName = "book_1.epub",
                    fileSize = 42,
                    checksum = "sum",
                    downloadedAtEpochMillis = 3,
                ),
            ),
        )

        val decoded = json.decodeFromString<LocalBookIndex>(json.encodeToString(index))

        assertEquals(index, decoded)
    }
}
