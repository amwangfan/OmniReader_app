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
data class LocalBook(
    val id: String,
    val title: String,
    val author: String = "",
    val fileName: String,
    val fileSize: Long,
    val checksum: String,
    val downloadedAtEpochMillis: Long,
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
