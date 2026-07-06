package com.amwangfan.omnireader.data

import com.amwangfan.omnireader.reader.EpubParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalBookStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var files: FakeManagedBookFiles
    private lateinit var store: LocalBookStore

    @Before
    fun setUp() {
        files = FakeManagedBookFiles(temporaryFolder.root)
        store = LocalBookStore(
            indexFile = temporaryFolder.newFile("local-books.json").apply { delete() },
            internalBooksDir = temporaryFolder.newFolder("epubs"),
            managedFiles = files,
        )
    }

    @Test
    fun download_recordsBookOnlyAfterWriterSucceeds() = runTest {
        val local = store.download(remoteBook(), StorageDestination.Internal) { output ->
            output.write("epub".toByteArray())
            4
        }

        assertEquals("remote-1", local.remoteBookId)
        assertEquals(BookSyncState.SYNCED, local.syncState)
        assertEquals(listOf(local), store.loadBooks())
        assertEquals("epub", files.contents.getValue(local.fileName).decodeToString())
    }

    @Test
    fun failedDownload_discardsOutputAndLeavesIndexEmpty() = runTest {
        val failure = runCatching {
            store.download(remoteBook(), StorageDestination.Internal) { output ->
                output.write("partial".toByteArray())
                error("network failed")
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, files.discardCount)
        assertTrue(store.loadBooks().isEmpty())
        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun update_validatesNewEpubBeforeSwitchingManagedFile() = runTest {
        val original = store.download(remoteBook("revision-1"), StorageDestination.Internal) { output ->
            output.write("old epub".toByteArray())
            8
        }
        val updatedBytes = fixtureEpubBytes("Updated Book")

        val updated = store.update(remoteBook("revision-2"), EpubParser()) { output ->
            output.write(updatedBytes)
            updatedBytes.size.toLong()
        }

        assertEquals("revision-2", updated.contentRevision)
        assertEquals("Updated Book", EpubParser().parse(store.materialize(updated)).title)
        assertFalse(files.contents.containsKey(original.fileName))
        assertEquals(listOf(updated), store.loadBooks())
    }

    @Test
    fun update_parseFailureKeepsOldManagedFileAndIndex() = runTest {
        val original = store.download(remoteBook("revision-1"), StorageDestination.Internal) { output ->
            output.write("old epub".toByteArray())
            8
        }

        val failure = runCatching {
            store.update(remoteBook("revision-2"), EpubParser()) { output ->
                output.write("not an epub".toByteArray())
                11
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(original), store.loadBooks())
        assertEquals("old epub", files.contents.getValue(original.fileName).decodeToString())
        assertEquals(1, files.discardCount)
    }

    @Test
    fun update_documentBookStagesReplacementInOriginalTree() = runTest {
        val tree = StorageDestination.Tree("content://library")
        store.download(remoteBook("revision-1"), tree) { output ->
            output.write("old epub".toByteArray())
            8
        }
        val updatedBytes = fixtureEpubBytes("Updated Book")

        val updated = store.update(remoteBook("revision-2"), EpubParser()) { output ->
            output.write(updatedBytes)
            updatedBytes.size.toLong()
        }

        assertEquals(tree, files.createdDestinations.last())
        assertEquals("content://library", updated.storageTreeUri)
    }

    @Test
    fun duplicateImport_discardsSecondCopyAndKeepsOneRecord() = runTest {
        val epub = fixtureEpubBytes()

        val first = store.importBook(
            source = ByteArrayInputStream(epub),
            sourceName = "sample.epub",
            destination = StorageDestination.Internal,
            autoUpload = true,
            parser = EpubParser(),
        )
        val second = store.importBook(
            source = ByteArrayInputStream(epub),
            sourceName = "sample.epub",
            destination = StorageDestination.Internal,
            autoUpload = true,
            parser = EpubParser(),
        )

        assertEquals(first, second)
        assertTrue(first.id.startsWith("local-"))
        assertEquals("Fixture Book", first.title)
        assertEquals("Fixture Author", first.author)
        assertEquals(BookSyncState.PENDING_UPLOAD, first.syncState)
        assertEquals(1, store.loadBooks().size)
        assertEquals(1, files.discardCount)
    }

    @Test
    fun documentImport_remembersTreeForFutureRevisionUpdates() = runTest {
        val local = store.importBook(
            source = ByteArrayInputStream(fixtureEpubBytes()),
            sourceName = "sample.epub",
            destination = StorageDestination.Tree("content://library"),
            autoUpload = false,
            parser = EpubParser(),
        )

        assertEquals("content://library", local.storageTreeUri)
    }

    @Test
    fun treeUriFromDocumentUri_derivesTypicalSafTreeWithoutDocumentPath() {
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            treeUriFromDocumentUri(
                "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.epub",
            ),
        )
        assertEquals(null, treeUriFromDocumentUri("content://provider/document/book"))
        assertEquals(null, treeUriFromDocumentUri("https://provider/tree/root/document/book"))
    }

    @Test
    fun markUploaded_recordsRemoteIdentityAndSyncedState() = runTest {
        val local = store.importBook(
            source = ByteArrayInputStream(fixtureEpubBytes()),
            sourceName = "sample.epub",
            destination = StorageDestination.Internal,
            autoUpload = true,
            parser = EpubParser(),
        )

        val updated = store.markUploaded(local.id, remoteBook())

        assertEquals("remote-1", updated.remoteBookId)
        assertEquals(BookSyncState.SYNCED, updated.syncState)
        assertTrue(store.pendingUploads().isEmpty())
    }

    @Test
    fun deleteFailure_preservesIndexWhileMissingFileRemovesIt() = runTest {
        val local = store.download(remoteBook(), StorageDestination.Internal) { output ->
            output.write("epub".toByteArray())
            4
        }
        files.nextDeleteResult = ManagedDeleteResult.FAILED

        assertFalse(store.delete(local.id))
        assertEquals(listOf(local), store.loadBooks())

        files.nextDeleteResult = ManagedDeleteResult.MISSING
        assertTrue(store.delete(local.id))
        assertTrue(store.loadBooks().isEmpty())
    }

    private fun remoteBook(contentRevision: String = "") = BookDto(
        id = "remote-1",
        title = "Remote Book",
        author = "Remote Author",
        format = "epub",
        fileSize = 4,
        checksum = "remote-sum",
        createdAt = "now",
        updatedAt = "now",
        contentRevision = contentRevision,
    )

    private fun fixtureEpubBytes(title: String = "Fixture Book"): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/content.opf"/></rootfiles></container>""",
            )
            zip.putText(
                "OPS/content.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title><dc:creator>Fixture Author</dc:creator></metadata><manifest><item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.putText(
                "OPS/chapter.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>Chapter</h1><p>Text</p></body></html>""",
            )
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }
}

private class FakeManagedBookFiles(
    private val tempDir: File,
) : ManagedBookFiles {
    val contents = mutableMapOf<String, ByteArray>()
    val createdDestinations = mutableListOf<StorageDestination>()
    var discardCount = 0
    var nextDeleteResult = ManagedDeleteResult.DELETED

    override fun create(destination: StorageDestination, displayName: String): ManagedOutput {
        createdDestinations += destination
        val fileName = "${contents.size}-${displayName}"
        val output = object : ByteArrayOutputStream() {
            override fun close() {
                contents[fileName] = toByteArray()
                super.close()
            }
        }
        return ManagedOutput(
            fileName = fileName,
            storageKind = if (destination is StorageDestination.Tree) StorageKind.DOCUMENT_URI else StorageKind.INTERNAL,
            documentUri = (destination as? StorageDestination.Tree)?.let { "${it.uri}/$fileName" },
            outputStream = output,
            discard = {
                discardCount += 1
                contents.remove(fileName)
            },
        )
    }

    override fun materialize(book: LocalBook): File {
        val bytes = contents[book.fileName] ?: error("missing managed bytes")
        return File.createTempFile("book-", ".epub", tempDir).apply { writeBytes(bytes) }
    }

    override fun delete(book: LocalBook): ManagedDeleteResult {
        if (nextDeleteResult == ManagedDeleteResult.DELETED) {
            contents.remove(book.fileName)
        }
        return nextDeleteResult
    }
}
