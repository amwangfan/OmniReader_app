package com.amwangfan.omnireader.data

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalBookStore(
    context: Context,
    private val json: Json = Json { prettyPrint = true },
) {
    private val booksDir = File(context.filesDir, "epubs")
    private val indexFile = File(context.filesDir, "local-books.json")
    private val mutex = Mutex()

    suspend fun loadBooks(): List<LocalBook> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            return@withContext emptyList()
        }
        runCatching {
            json.decodeFromString<LocalBookIndex>(indexFile.readText()).books
        }.getOrDefault(emptyList())
    }

    fun epubFile(bookId: String): File = File(booksDir, "$bookId.epub")

    suspend fun recordDownloaded(remote: BookDto, file: File): LocalBook = mutex.withLock {
        withContext(Dispatchers.IO) {
            booksDir.mkdirs()
            val existing = readIndex()
            val previous = existing.firstOrNull { it.id == remote.id }
            val local = LocalBook(
                id = remote.id,
                title = remote.title,
                author = remote.author,
                fileName = file.name,
                fileSize = file.length(),
                checksum = remote.checksum,
                downloadedAtEpochMillis = System.currentTimeMillis(),
                currentChapterIndex = previous?.currentChapterIndex ?: 0,
                progressUpdatedAtEpochMillis = previous?.progressUpdatedAtEpochMillis ?: 0,
            )
            writeIndex(LocalBookIndex(existing).upsert(local))
            local
        }
    }

    suspend fun updateProgress(bookId: String, chapterIndex: Int, updatedAtEpochMillis: Long): LocalBook? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = readIndex()
                val book = existing.firstOrNull { it.id == bookId } ?: return@withContext null
                val updated = book.copy(
                    currentChapterIndex = chapterIndex.coerceAtLeast(0),
                    progressUpdatedAtEpochMillis = updatedAtEpochMillis,
                )
                writeIndex(LocalBookIndex(existing).upsert(updated))
                updated
            }
        }

    fun fileFor(localBook: LocalBook): File = File(booksDir, localBook.fileName)

    private fun readIndex(): List<LocalBook> {
        if (!indexFile.exists()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<LocalBookIndex>(indexFile.readText()).books
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(index: LocalBookIndex) {
        val temporary = File(indexFile.parentFile, "${indexFile.name}.tmp")
        temporary.writeText(json.encodeToString(index))
        runCatching {
            Files.move(
                temporary.toPath(),
                indexFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
