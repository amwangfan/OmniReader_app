package com.amwangfan.omnireader.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val clientLabel: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: String,
)

@Serializable
data class BookDto(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String,
    val fileSize: Long,
    val checksum: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BooksResponse(
    val books: List<BookDto> = emptyList(),
)

@Serializable
enum class StorageKind {
    INTERNAL,
    DOCUMENT_URI,
}

@Serializable
enum class BookSource {
    SERVER_DOWNLOAD,
    LOCAL_IMPORT,
}

@Serializable
enum class BookSyncState {
    SYNCED,
    PENDING_UPLOAD,
    LOCAL_ONLY,
}

@Serializable
data class LocalBook(
    val id: String,
    val title: String,
    val author: String = "",
    val fileName: String,
    val fileSize: Long,
    val checksum: String,
    val downloadedAtEpochMillis: Long,
    val remoteBookId: String? = null,
    val storageKind: StorageKind = StorageKind.INTERNAL,
    val documentUri: String? = null,
    val source: BookSource = BookSource.SERVER_DOWNLOAD,
    val syncState: BookSyncState = BookSyncState.SYNCED,
) {
    fun normalized(): LocalBook =
        if (source == BookSource.SERVER_DOWNLOAD && remoteBookId == null) {
            copy(remoteBookId = id, syncState = BookSyncState.SYNCED)
        } else {
            this
        }
}

@Serializable
data class LocalBookIndex(
    val books: List<LocalBook> = emptyList(),
) {
    fun upsert(book: LocalBook): LocalBookIndex {
        val next = books.filterNot { it.id == book.id } + book
        return copy(books = next.sortedBy { it.title.lowercase() })
    }

    fun normalized(): LocalBookIndex = copy(books = books.map(LocalBook::normalized))

    fun remove(localId: String): LocalBookIndex = copy(books = books.filterNot { it.id == localId })

    fun pendingUploads(): List<LocalBook> = books.filter { it.syncState == BookSyncState.PENDING_UPLOAD }
}
