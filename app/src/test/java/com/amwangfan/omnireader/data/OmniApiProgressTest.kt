package com.amwangfan.omnireader.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniApiProgressTest {
    private val server = MockWebServer()
    private val api = OmniApi()

    @Test fun registerDevice_putsAuthenticatedExactJson() = runTest {
        server.enqueue(MockResponse().setBody(deviceJson()))
        val request = DeviceRegistrationRequest("d", "Display", "System", manufacturer="Onyx", model="Leaf", appVersion="0.3")
        assertEquals("d", api.registerDevice(server.url("/").toString(), "token", request).id)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method); assertEquals("/api/v1/devices/current", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
        assertEquals("""{"id":"d","displayName":"Display","systemName":"System","platform":"android","manufacturer":"Onyx","model":"Leaf","appVersion":"0.3"}""", recorded.body.readUtf8())
    }

    @Test fun getProgress_encodesDeviceIdQuery() = runTest {
        server.enqueue(MockResponse().setBody(progressJson()))
        api.getProgress(server.url("/").toString(), "token", "book id", "device/id")
        assertEquals("/api/v1/books/book%20id/progress?deviceId=device%2Fid", server.takeRequest().path)
    }

    @Test fun putProgress_sendsAbsoluteDailyTotals() = runTest {
        server.enqueue(MockResponse().setBody(progressJson()))
        api.putProgress(server.url("/").toString(), "token", "b", ProgressPutRequest("d", locator(), .2, null, mapOf("2026-07-06" to 9)))
        val request = server.takeRequest()
        assertEquals("PUT", request.method); assertTrue(request.body.readUtf8().contains("\"dailyReadSeconds\":{\"2026-07-06\":9}"))
    }

    @Test fun statusErrors_keepStatusCode() = runTest {
        for (code in listOf(400, 403, 404)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("error-$code"))
            val error = runCatching { api.getProgress(server.url("/").toString(), "t", "b", "d") }.exceptionOrNull() as ApiException
            assertEquals(code, error.statusCode)
        }
    }

    private fun locator() = ReadingLocator(contentRevision="r", chapterHref="c", chapterIndex=0, blockIndex=0)
    private fun deviceJson() = """{"id":"d","displayName":"Display","systemName":"System","platform":"android","manufacturer":"Onyx","model":"Leaf","appVersion":"0.3","lastSeenAt":"now","disabledAt":null}"""
    private fun progressJson() = """{"deviceProgress":null,"globalProgress":null,"contentRevision":"r"}"""
}
