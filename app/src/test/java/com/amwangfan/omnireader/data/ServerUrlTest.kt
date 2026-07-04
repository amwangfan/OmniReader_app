package com.amwangfan.omnireader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlTest {
    @Test
    fun normalizeServerBaseUrl_trimsTrailingSlashes() {
        assertEquals("http://100.114.93.90:18080", normalizeServerBaseUrl(" http://100.114.93.90:18080/// "))
    }

    @Test
    fun normalizeServerBaseUrl_rejectsMissingScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerBaseUrl("100.114.93.90:18080")
        }
    }
}
