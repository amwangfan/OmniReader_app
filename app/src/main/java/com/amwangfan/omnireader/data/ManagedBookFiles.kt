package com.amwangfan.omnireader.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream

interface ManagedBookFiles {
    fun create(destination: StorageDestination, displayName: String): ManagedOutput

    fun materialize(book: LocalBook): File

    fun delete(book: LocalBook): ManagedDeleteResult
}

data class ManagedOutput(
    val fileName: String,
    val storageKind: StorageKind,
    val documentUri: String?,
    val outputStream: OutputStream,
    val discard: () -> Unit,
)

enum class ManagedDeleteResult {
    DELETED,
    MISSING,
    FAILED,
}

class AndroidManagedBookFiles(
    context: Context,
) : ManagedBookFiles {
    private val contentResolver = context.contentResolver
    private val booksDir = File(context.filesDir, "epubs")
    private val readerCacheDir = File(context.cacheDir, "reader")

    override fun create(destination: StorageDestination, displayName: String): ManagedOutput {
        val safeName = safeEpubName(displayName)
        return when (destination) {
            StorageDestination.Internal -> createInternal(safeName)
            is StorageDestination.Tree -> createDocument(Uri.parse(destination.uri), safeName)
        }
    }

    override fun materialize(book: LocalBook): File = when (book.storageKind) {
        StorageKind.INTERNAL -> File(booksDir, book.fileName).also { file ->
            check(file.isFile) { "EPUB file is missing: ${book.title}" }
        }
        StorageKind.DOCUMENT_URI -> {
            val uri = book.documentUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
                ?: error("Stored document URI is missing")
            readerCacheDir.mkdirs()
            val cacheFile = File(readerCacheDir, "${safeFilePart(book.id)}.epub")
            val input = contentResolver.openInputStream(uri)
                ?: error("Cannot open stored EPUB: ${book.title}")
            input.use { source ->
                cacheFile.outputStream().use(source::copyTo)
            }
            cacheFile
        }
    }

    override fun delete(book: LocalBook): ManagedDeleteResult = try {
        when (book.storageKind) {
            StorageKind.INTERNAL -> {
                val file = File(booksDir, book.fileName)
                when {
                    !file.exists() -> ManagedDeleteResult.MISSING
                    file.delete() -> ManagedDeleteResult.DELETED
                    else -> ManagedDeleteResult.FAILED
                }
            }
            StorageKind.DOCUMENT_URI -> {
                val uri = book.documentUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
                    ?: return ManagedDeleteResult.FAILED
                val document = DocumentFile.fromSingleUri(contentResolverContext, uri)
                    ?: return ManagedDeleteResult.FAILED
                when {
                    !document.exists() -> ManagedDeleteResult.MISSING
                    document.delete() -> ManagedDeleteResult.DELETED
                    else -> ManagedDeleteResult.FAILED
                }
            }
        }
    } catch (_: SecurityException) {
        ManagedDeleteResult.FAILED
    }

    private val contentResolverContext: Context = context.applicationContext

    private fun createInternal(fileName: String): ManagedOutput {
        booksDir.mkdirs()
        val file = File(booksDir, fileName)
        return ManagedOutput(
            fileName = file.name,
            storageKind = StorageKind.INTERNAL,
            documentUri = null,
            outputStream = file.outputStream(),
            discard = { file.delete() },
        )
    }

    private fun createDocument(treeUri: Uri, fileName: String): ManagedOutput {
        val tree = DocumentFile.fromTreeUri(contentResolverContext, treeUri)
            ?: error("Cannot access selected folder")
        tree.findFile(fileName)?.delete()
        val document = tree.createFile(EPUB_MIME_TYPE, fileName)
            ?: error("Cannot create EPUB in selected folder")
        val output = contentResolver.openOutputStream(document.uri, "w")
            ?: error("Cannot write EPUB in selected folder")
        return ManagedOutput(
            fileName = document.name ?: fileName,
            storageKind = StorageKind.DOCUMENT_URI,
            documentUri = document.uri.toString(),
            outputStream = output,
            discard = { document.delete() },
        )
    }

    private fun safeEpubName(value: String): String {
        val name = File(value).name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "book.epub" }
        return if (name.endsWith(".epub", ignoreCase = true)) name else "$name.epub"
    }

    private fun safeFilePart(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "book" }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
    }
}
