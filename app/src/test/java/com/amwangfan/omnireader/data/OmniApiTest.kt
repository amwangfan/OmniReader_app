package com.amwangfan.omnireader.data

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OmniApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OmniApi

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        server = MockWebServer().also(MockWebServer::start)
        api = OmniApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun downloadBook_writesResponseToCallerOutput() = runTest {
        val expected = "epub-bytes".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(expected)))
        val output = ByteArrayOutputStream()

        val count = api.downloadBook(
            server.url("/").toString(),
            "token",
            "book-1",
            output,
        )

        assertEquals(expected.size.toLong(), count)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun uploadBook_sendsAuthenticatedMultipartEpub() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"book":{"id":"remote-1","title":"Imported","author":"","format":"epub","fileSize":4,"checksum":"sum","createdAt":"now","updatedAt":"now"}}""",
            ),
        )
        val file = temporaryFolder.newFile("sample.epub").apply {
            writeBytes("epub".toByteArray())
        }

        val result = api.uploadBook(
            server.url("/").toString(),
            "token",
            "Imported",
            file,
        )
        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()

        assertEquals("POST", request.method)
        assertEquals("/api/v1/books", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(requestBody.contains("name=\"title\""))
        assertTrue(requestBody.contains("Imported"))
        assertTrue(requestBody.contains("filename=\"sample.epub\""))
        assertEquals("remote-1", result.id)
    }
}
