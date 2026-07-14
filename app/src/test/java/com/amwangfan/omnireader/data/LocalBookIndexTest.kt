package com.amwangfan.omnireader.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBookIndexTest {
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

    @Test
    fun localBookIndex_readsLegacyEntriesWithoutProgressFields() {
        val decoded = Json.decodeFromString<LocalBookIndex>(
            `{"books":[{"id":"book_1","title":"Book","fileName":"book_1.epub","fileSize":42,"checksum":"sum","downloadedAtEpochMillis":3}]}`,
        )

        assertEquals(0, decoded.books.single().currentChapterIndex)
        assertEquals(0, decoded.books.single().progressUpdatedAtEpochMillis)
    }
}
