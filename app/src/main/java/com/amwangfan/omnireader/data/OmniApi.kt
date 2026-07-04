package com.amwangfan.omnireader.data

import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class OmniApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(baseUrl: String, username: String, password: String): LoginResponse =
        withContext(Dispatchers.IO) {
            val requestBody = json.encodeToString(
                LoginRequest(username = username, password = password, clientLabel = "android-app"),
            ).toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${normalizeServerBaseUrl(baseUrl)}/api/v1/auth/login")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, body.ifBlank { "Login failed" })
                }
                json.decodeFromString<LoginResponse>(body)
            }
        }

    suspend fun listBooks(baseUrl: String, accessToken: String): List<BookDto> =
        withContext(Dispatchers.IO) {
            val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, body.ifBlank { "List books failed" })
                }
                json.decodeFromString<BooksResponse>(body).books
            }
        }

    suspend fun downloadBook(
        baseUrl: String,
        accessToken: String,
        bookId: String,
        targetFile: File,
    ): Long = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { output ->
            downloadBook(baseUrl, accessToken, bookId, output)
        }
    }

    suspend fun downloadBook(
        baseUrl: String,
        accessToken: String,
        bookId: String,
        output: OutputStream,
    ): Long = withContext(Dispatchers.IO) {
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books/$bookId/download")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw ApiException(response.code, body.ifBlank { "Download failed" })
            }
            val responseBody = response.body ?: throw ApiException(response.code, "Empty download body")
            responseBody.byteStream().use { input -> input.copyTo(output) }
        }
    }

    suspend fun uploadBook(
        baseUrl: String,
        accessToken: String,
        title: String,
        file: File,
    ): BookDto = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/epub+zip".toMediaType()),
            )
            .build()
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books")
            .post(multipart)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body.ifBlank { "Upload failed" })
            }
            json.decodeFromString<BookResponse>(body).book
        }
    }

    private fun authorizedBuilder(baseUrl: String, token: String, path: String): Request.Builder =
        Request.Builder()
            .url("${normalizeServerBaseUrl(baseUrl)}$path")
            .header("Authorization", "Bearer $token")
}

class ApiException(
    val statusCode: Int,
    override val message: String,
) : RuntimeException(message)
