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
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
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
data class DeviceRequest(
    val id: String,
    val displayName: String,
    val platform: String = "android",
)

@Serializable
data class DeviceDto(
    val id: String,
    val displayName: String,
    val platform: String,
    val lastSeenAt: String,
)

@Serializable
data class DeviceResponse(
    val device: DeviceDto,
)

@Serializable
data class ProgressDto(
    val bookId: String,
    val deviceId: String,
    val locator: String,
    val percentage: Double? = null,
    val updatedAt: String,
)

@Serializable
data class ProgressResponse(
    val progress: ProgressDto? = null,
)

@Serializable
data class PutProgressRequest(
    val deviceId: String,
    val locator: String,
    val percentage: Double? = null,
    val updatedAt: String,
)

@Serializable
data class LocalBook(
    val id: String,
    val title: String,
    val author: String = "",
    val fileName: String,
    val fileSize: Long,
    val checksum: String,
    val downloadedAtEpochMillis: Long,
    val currentChapterIndex: Int = 0,
    val progressUpdatedAtEpochMillis: Long = 0,
)

@Serializable
data class LocalBookIndex(
    val books: List<LocalBook> = emptyList(),
) {
    fun upsert(book: LocalBook): LocalBookIndex {
        val next = books.filterNot { it.id == book.id } + book
        return copy(books = next.sortedBy { it.title.lowercase() })
    }
}
