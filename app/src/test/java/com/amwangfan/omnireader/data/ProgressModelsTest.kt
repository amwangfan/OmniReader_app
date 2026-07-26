package com.amwangfan.omnireader.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProgressModelsTest {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Test fun locator_usesExactCamelCaseContract() {
        val locator = locator()
        assertEquals(
            """{"version":1,"contentRevision":"rev","chapterHref":"OPS/c1.xhtml","chapterIndex":2,"blockIndex":3,"charOffset":4,"textQuote":"quote","textHash":"hash","chapterProgress":0.5,"bookProgress":0.25}""",
            json.encodeToString(locator),
        )
        assertEquals(locator, json.decodeFromString<ReadingLocator>(json.encodeToString(locator)))
    }

    @Test fun registration_usesExactCamelCaseContract() {
        val request = DeviceRegistrationRequest("uuid", "My Reader", "My Reader", manufacturer = "Onyx", model = "Leaf", appVersion = "0.3")
        assertEquals(
            """{"id":"uuid","displayName":"My Reader","systemName":"My Reader","platform":"android","manufacturer":"Onyx","model":"Leaf","appVersion":"0.3"}""",
            json.encodeToString(request),
        )
    }

    @Test fun progressPut_roundTripsDailyAbsoluteTotals() {
        val request = ProgressPutRequest("uuid", locator(), 0.25, null, linkedMapOf("2026-07-05" to 12, "2026-07-06" to 34))
        assertEquals(request, json.decodeFromString<ProgressPutRequest>(json.encodeToString(request)))
        assertEquals(34L, request.dailyReadSeconds["2026-07-06"])
    }

    @Test fun deviceResponse_matchesServerFieldsIncludingNullableDisabledAt() {
        val device = DeviceDto("d", "Display", "System", "android", "Onyx", "Leaf", "0.3.0", "seen", null)
        assertEquals(
            """{"id":"d","displayName":"Display","systemName":"System","platform":"android","manufacturer":"Onyx","model":"Leaf","appVersion":"0.3.0","lastSeenAt":"seen","disabledAt":null}""",
            json.encodeToString(device),
        )
    }

    @Test fun legacyBookJson_defaultsContentRevision() {
        val book = Json.decodeFromString<BookDto>("""{"id":"b","title":"T","format":"epub","fileSize":1,"checksum":"x","createdAt":"c","updatedAt":"u"}""")
        assertEquals("", book.contentRevision)
        assertFalse(json.encodeToString(book).contains("content_revision"))
    }

    private fun locator() = ReadingLocator(1, "rev", "OPS/c1.xhtml", 2, 3, 4, "quote", "hash", 0.5, 0.25)
}
