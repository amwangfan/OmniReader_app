package com.amwangfan.omnireader.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

    suspend fun refresh(baseUrl: String, refreshToken: String): RefreshResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${normalizeServerBaseUrl(baseUrl)}/api/v1/auth/refresh")
                .post(json.encodeToString(RefreshRequest(refreshToken)).toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(response.code, body.ifBlank { "Session refresh failed" })
                }
                json.decodeFromString<RefreshResponse>(body)
            }
        }

    suspend fun logout(baseUrl: String, refreshToken: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${normalizeServerBaseUrl(baseUrl)}/api/v1/auth/logout")
            .post(json.encodeToString(LogoutRequest(refreshToken)).toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.body?.string().orEmpty().ifBlank { "Logout failed" })
            }
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
        expectedChecksum: String,
    ): Long = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        val partialFile = File(targetFile.parentFile, "${targetFile.name}.part")
        partialFile.delete()
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books/$bookId/download")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw ApiException(response.code, body.ifBlank { "Download failed" })
                }
                val responseBody = response.body ?: throw ApiException(response.code, "Empty download body")
                partialFile.outputStream().use { output ->
                    responseBody.byteStream().use { input -> input.copyTo(output) }
                }
            }
            val actualChecksum = sha256(partialFile)
            if (expectedChecksum.isNotBlank() && !actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw ApiException(422, "Downloaded EPUB checksum mismatch")
            }
            moveReplacing(partialFile, targetFile)
        } catch (error: Throwable) {
            partialFile.delete()
            throw error
        }
        targetFile.length()
    }

    suspend fun upsertDevice(
        baseUrl: String,
        accessToken: String,
        requestBody: DeviceRequest,
    ): DeviceDto = withContext(Dispatchers.IO) {
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/devices/current")
            .put(json.encodeToString(requestBody).toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body.ifBlank { "Device registration failed" })
            }
            json.decodeFromString<DeviceResponse>(body).device
        }
    }

    suspend fun getProgress(
        baseUrl: String,
        accessToken: String,
        bookId: String,
    ): ProgressDto? = withContext(Dispatchers.IO) {
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books/$bookId/progress")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body.ifBlank { "Reading progress download failed" })
            }
            json.decodeFromString<ProgressResponse>(body).progress
        }
    }

    suspend fun putProgress(
        baseUrl: String,
        accessToken: String,
        bookId: String,
        progress: PutProgressRequest,
    ): ProgressDto = withContext(Dispatchers.IO) {
        val request = authorizedBuilder(baseUrl, accessToken, "/api/v1/books/$bookId/progress")
            .put(json.encodeToString(progress).toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body.ifBlank { "Reading progress upload failed" })
            }
            json.decodeFromString<ProgressResponse>(body).progress
                ?: throw ApiException(response.code, "Progress response was empty")
        }
    }

    private fun authorizedBuilder(baseUrl: String, token: String, path: String): Request.Builder =
        Request.Builder()
            .url("${normalizeServerBaseUrl(baseUrl)}$path")
            .header("Authorization", "Bearer $token")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun moveReplacing(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class ApiException(
    val statusCode: Int,
    override val message: String,
) : RuntimeException(message)
