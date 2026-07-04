package com.amwangfan.omnireader.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalBookStore(
    context: Context,
    private val json: Json = Json { prettyPrint = true },
) {
    private val booksDir = File(context.filesDir, "epubs")
    private val indexFile = File(context.filesDir, "local-books.json")

    suspend fun loadBooks(): List<LocalBook> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            return@withContext emptyList()
        }
        runCatching {
            json.decodeFromString<LocalBookIndex>(indexFile.readText()).books
        }.getOrDefault(emptyList())
    }

    fun epubFile(bookId: String): File = File(booksDir, "$bookId.epub")

    suspend fun recordDownloaded(remote: BookDto, file: File): LocalBook = withContext(Dispatchers.IO) {
        booksDir.mkdirs()
        val existing = loadBooks()
        val local = LocalBook(
            id = remote.id,
            title = remote.title,
            author = remote.author,
            fileName = file.name,
            fileSize = file.length(),
            checksum = remote.checksum,
            downloadedAtEpochMillis = System.currentTimeMillis(),
        )
        val next = LocalBookIndex(existing).upsert(local)
        indexFile.writeText(json.encodeToString(next))
        local
    }

    fun fileFor(localBook: LocalBook): File = File(booksDir, localBook.fileName)
}
