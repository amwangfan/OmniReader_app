package com.amwangfan.omnireader.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReadingStateRecord(
    val locator: ReadingLocator,
    val dailyReadSeconds: Map<String, Long> = emptyMap(),
    val dirty: Boolean = false,
    val lastServerUpdatedAt: String? = null,
    val generation: Long = 0,
)

@Serializable
private data class ReadingStateFile(
    val version: Int = 1,
    val books: Map<String, Map<String, ReadingStateRecord>> = emptyMap(),
)

class ReadingStateStore(
    directory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    private val file = File(directory, "reading_state.json")
    private val temp = File(directory, "reading_state.json.tmp")
    private var state: ReadingStateFile = load()

    @Synchronized fun get(bookId: String, deviceId: String): ReadingStateRecord? = state.books[bookId]?.get(deviceId)

    @Synchronized fun put(bookId: String, deviceId: String, record: ReadingStateRecord) {
        val devices = state.books[bookId].orEmpty() + (deviceId to record)
        state = state.copy(books = state.books + (bookId to devices))
        persist()
    }

    @Synchronized fun dirtyRecords(): List<Triple<String, String, ReadingStateRecord>> = state.books.flatMap { (book, devices) ->
        devices.filterValues { it.dirty }.map { (device, record) -> Triple(book, device, record) }
    }

    @Synchronized fun markClean(bookId: String, deviceId: String, serverUpdatedAt: String?) {
        val record = get(bookId, deviceId) ?: return
        markCleanIfUnchanged(bookId, deviceId, record.generation, serverUpdatedAt)
    }

    @Synchronized fun markCleanIfUnchanged(bookId: String, deviceId: String, generation: Long, serverUpdatedAt: String?): Boolean {
        val record = get(bookId, deviceId) ?: return false
        if (record.generation != generation) return false
        put(bookId, deviceId, record.copy(dirty = false, lastServerUpdatedAt = serverUpdatedAt))
        return true
    }

    @Synchronized fun mergeElapsed(bookId: String, deviceId: String, elapsed: Map<String, Long>, locator: ReadingLocator) {
        val old = get(bookId, deviceId)
        val totals = old?.dailyReadSeconds.orEmpty().toMutableMap()
        elapsed.forEach { (date, seconds) -> totals[date] = totals.getOrDefault(date, 0) + seconds.coerceAtLeast(0) }
        put(bookId, deviceId, ReadingStateRecord(locator, totals, dirty = true, lastServerUpdatedAt = old?.lastServerUpdatedAt, generation = (old?.generation ?: 0) + 1))
    }

    @Synchronized fun migrateBookId(
        localBookId: String,
        remoteBookId: String,
        deviceId: String,
        contentRevision: String,
    ) {
        if (localBookId == remoteBookId) return
        val source = get(localBookId, deviceId) ?: return
        val target = get(remoteBookId, deviceId)
        val totals = target?.dailyReadSeconds.orEmpty().toMutableMap()
        source.dailyReadSeconds.forEach { (date, seconds) -> totals[date] = maxOf(totals.getOrDefault(date, 0), seconds) }
        val migrated = source.copy(
            locator = source.locator.copy(contentRevision = contentRevision),
            dailyReadSeconds = totals,
            dirty = source.dirty || target?.dirty == true,
            generation = maxOf(source.generation, target?.generation ?: 0) + 1,
        )
        val localDevices = state.books[localBookId].orEmpty() - deviceId
        var books = if (localDevices.isEmpty()) state.books - localBookId else state.books + (localBookId to localDevices)
        books = books + (remoteBookId to (books[remoteBookId].orEmpty() + (deviceId to migrated)))
        state = state.copy(books = books)
        persist()
    }

    private fun load(): ReadingStateFile {
        if (!file.exists() || file.length() == 0L) return ReadingStateFile()
        return runCatching { json.decodeFromString<ReadingStateFile>(file.readText()) }.getOrElse {
            val corrupt = File(file.parentFile, "${file.name}.corrupt-${nowMillis()}")
            runCatching { Files.move(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            ReadingStateFile()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        FileOutputStream(temp).use { stream ->
            stream.bufferedWriter().use { output ->
                output.write(json.encodeToString(state))
                output.flush()
                stream.fd.sync()
            }
        }
        runCatching {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
