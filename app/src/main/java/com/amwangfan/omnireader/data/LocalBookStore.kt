package com.amwangfan.omnireader.data

import android.content.Context
import com.amwangfan.omnireader.reader.EpubParser
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalBookStore internal constructor(
    private val indexFile: File,
    private val internalBooksDir: File,
    private val managedFiles: ManagedBookFiles,
    private val json: Json = Json { prettyPrint = true },
) {
    constructor(
        context: Context,
        json: Json = Json { prettyPrint = true },
    ) : this(
        indexFile = File(context.filesDir, "local-books.json"),
        internalBooksDir = File(context.filesDir, "epubs"),
        managedFiles = AndroidManagedBookFiles(context),
        json = json,
    )

    private val indexMutex = Mutex()

    suspend fun loadBooks(): List<LocalBook> = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            val stored = readIndex()
            val normalized = stored.normalized()
            if (stored != normalized) {
                writeIndex(normalized)
            }
            normalized.books
        }
    }

    fun epubFile(bookId: String): File = File(internalBooksDir, "$bookId.epub")

    suspend fun recordDownloaded(remote: BookDto, file: File): LocalBook = withContext(Dispatchers.IO) {
        internalBooksDir.mkdirs()
        val local = LocalBook(
            id = remote.id,
            title = remote.title,
            author = remote.author,
            fileName = file.name,
            fileSize = file.length(),
            checksum = remote.checksum,
            downloadedAtEpochMillis = System.currentTimeMillis(),
            remoteBookId = remote.id,
            source = BookSource.SERVER_DOWNLOAD,
            syncState = BookSyncState.SYNCED,
            contentRevision = remote.contentRevision,
        )
        updateIndex { it.upsert(local) }
        local
    }

    suspend fun download(
        remote: BookDto,
        destination: StorageDestination,
        writer: suspend (OutputStream) -> Long,
    ): LocalBook = withContext(Dispatchers.IO) {
        val output = managedFiles.create(destination, "${remote.id}.epub")
        try {
            val size = output.outputStream.use { writer(it) }
            val local = LocalBook(
                id = remote.id,
                title = remote.title,
                author = remote.author,
                fileName = output.fileName,
                fileSize = size,
                checksum = remote.checksum,
                downloadedAtEpochMillis = System.currentTimeMillis(),
                remoteBookId = remote.id,
                storageKind = output.storageKind,
                documentUri = output.documentUri,
                storageTreeUri = (destination as? StorageDestination.Tree)?.uri,
                source = BookSource.SERVER_DOWNLOAD,
                syncState = BookSyncState.SYNCED,
                contentRevision = remote.contentRevision,
            )
            updateIndex { it.upsert(local) }
            local
        } catch (error: Throwable) {
            runCatching(output.discard)
            throw error
        }
    }

    suspend fun update(
        remote: BookDto,
        parser: EpubParser,
        fallbackTreeUri: String? = null,
        writer: suspend (OutputStream) -> Long,
    ): LocalBook = withContext(Dispatchers.IO) {
        val current = loadBooks().firstOrNull { it.remoteBookId == remote.id }
            ?: error("Downloaded book not found: ${remote.id}")
        val destination = when (current.storageKind) {
            StorageKind.INTERNAL -> StorageDestination.Internal
            StorageKind.DOCUMENT_URI -> StorageDestination.Tree(
                current.storageTreeUri?.takeIf(String::isNotBlank)
                    ?: current.documentUri?.let(::treeUriFromDocumentUri)
                    ?: fallbackTreeUri?.takeIf(String::isNotBlank)
                    ?: error("The original download folder must be selected again before updating this book"),
            )
        }
        val output = managedFiles.create(
            destination,
            "${remote.id}-${UUID.randomUUID()}.epub",
        )
        val replacement = try {
            val size = output.outputStream.use { writer(it) }
            val candidate = current.copy(
                title = remote.title,
                author = remote.author,
                fileName = output.fileName,
                fileSize = size,
                checksum = remote.checksum,
                downloadedAtEpochMillis = System.currentTimeMillis(),
                storageKind = output.storageKind,
                documentUri = output.documentUri,
                storageTreeUri = (destination as? StorageDestination.Tree)?.uri,
                contentRevision = remote.contentRevision,
            )
            parser.parse(managedFiles.materialize(candidate))
            updateIndex { it.upsert(candidate) }
            candidate
        } catch (error: Throwable) {
            runCatching(output.discard)
            throw error
        }
        val deleteResult = runCatching { managedFiles.delete(current) }.getOrNull()
        if (deleteResult == null || deleteResult == ManagedDeleteResult.FAILED) {
            System.err.println("OmniReader: old EPUB cleanup remains pending for ${current.fileName}")
        }
        replacement
    }

    suspend fun importBook(
        source: InputStream,
        sourceName: String,
        destination: StorageDestination,
        autoUpload: Boolean,
        parser: EpubParser,
    ): LocalBook = withContext(Dispatchers.IO) {
        val output = managedFiles.create(destination, "${UUID.randomUUID()}-$sourceName")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            source.use { input ->
                output.outputStream.use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        size += count
                    }
                }
            }
            val provisional = LocalBook(
                id = "pending-import",
                title = sourceName.substringBeforeLast('.'),
                fileName = output.fileName,
                fileSize = size,
                checksum = "",
                downloadedAtEpochMillis = System.currentTimeMillis(),
                storageKind = output.storageKind,
                documentUri = output.documentUri,
                storageTreeUri = (destination as? StorageDestination.Tree)?.uri,
                source = BookSource.LOCAL_IMPORT,
                syncState = if (autoUpload) BookSyncState.PENDING_UPLOAD else BookSyncState.LOCAL_ONLY,
            )
            val document = parser.parse(managedFiles.materialize(provisional))
            val checksum = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            val local = provisional.copy(
                id = "local-$checksum",
                title = document.title,
                author = document.author,
                checksum = checksum,
            )
            var result = local
            var duplicate = false
            updateIndex { index ->
                val existing = index.books.firstOrNull { it.id == local.id }
                if (existing != null) {
                    result = existing
                    duplicate = true
                    index
                } else {
                    index.upsert(local)
                }
            }
            if (duplicate) {
                runCatching(output.discard)
            }
            result
        } catch (error: Throwable) {
            runCatching(output.discard)
            throw error
        }
    }

    suspend fun markUploaded(localId: String, remote: BookDto): LocalBook {
        var updated: LocalBook? = null
        updateIndex { index ->
            val current = index.books.firstOrNull { it.id == localId }
                ?: error("Local book not found: $localId")
            val next = current.copy(
                title = remote.title.ifBlank { current.title },
                author = remote.author.ifBlank { current.author },
                remoteBookId = remote.id,
                syncState = BookSyncState.SYNCED,
                contentRevision = remote.contentRevision,
            )
            updated = next
            index.upsert(next)
        }
        return checkNotNull(updated)
    }

    suspend fun delete(localId: String): Boolean {
        val book = loadBooks().firstOrNull { it.id == localId } ?: return false
        return when (managedFiles.delete(book)) {
            ManagedDeleteResult.DELETED,
            ManagedDeleteResult.MISSING,
            -> {
                updateIndex { it.remove(localId) }
                true
            }
            ManagedDeleteResult.FAILED -> false
        }
    }

    suspend fun pendingUploads(): List<LocalBook> = loadBooks().filter {
        it.syncState == BookSyncState.PENDING_UPLOAD
    }

    suspend fun materialize(book: LocalBook): File = withContext(Dispatchers.IO) {
        managedFiles.materialize(book)
    }

    fun fileFor(localBook: LocalBook): File = managedFiles.materialize(localBook)

    private suspend fun updateIndex(transform: (LocalBookIndex) -> LocalBookIndex): LocalBookIndex =
        withContext(Dispatchers.IO) {
            indexMutex.withLock {
                val updated = transform(readIndex().normalized())
                writeIndex(updated)
                updated
            }
        }

    private fun readIndex(): LocalBookIndex {
        if (!indexFile.isFile) return LocalBookIndex()
        return runCatching {
            json.decodeFromString<LocalBookIndex>(indexFile.readText())
        }.getOrDefault(LocalBookIndex())
    }

    private fun writeIndex(index: LocalBookIndex) {
        indexFile.parentFile?.mkdirs()
        val tempFile = File(indexFile.parentFile, "${indexFile.name}.tmp")
        FileOutputStream(tempFile).use { stream ->
            stream.bufferedWriter().use { output ->
                output.write(json.encodeToString(index))
                output.flush()
                stream.fd.sync()
            }
        }
        runCatching {
            Files.move(
                tempFile.toPath(),
                indexFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(tempFile.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal fun treeUriFromDocumentUri(documentUri: String): String? {
    if (!documentUri.startsWith("content://")) return null
    val treeMarker = "/tree/"
    val documentMarker = "/document/"
    val treeStart = documentUri.indexOf(treeMarker)
    if (treeStart < 0) return null
    val treeIdStart = treeStart + treeMarker.length
    val documentStart = documentUri.indexOf(documentMarker, treeIdStart)
    if (documentStart <= treeIdStart) return null
    val encodedTreeId = documentUri.substring(treeIdStart, documentStart)
    if (encodedTreeId.isBlank() || '/' in encodedTreeId) return null
    return documentUri.substring(0, documentStart)
}
