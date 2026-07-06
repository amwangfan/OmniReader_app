package com.amwangfan.omnireader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdentityTest {
    @Test fun existingUuid_isReusedWithoutWriting() {
        var writes = 0
        val id = getOrCreateStableId({ "existing" }, { writes++ }, { "new" })
        assertEquals("existing", id)
        assertEquals(0, writes)
    }

    @Test fun missingUuid_isGeneratedAndPersistedOnce() {
        var stored = ""
        val id = getOrCreateStableId({ stored }, { stored = it }, { "generated" })
        assertEquals("generated", id)
        assertEquals("generated", stored)
    }

    @Test fun readableSystemName_wins() {
        assertEquals("Living Room Leaf", defaultDeviceName(" Living Room Leaf ", "ONYX", "Leaf5"))
    }

    @Test fun manufacturerAndModel_formFallbackWithoutDuplication() {
        assertEquals("Onyx Leaf5", defaultDeviceName(null, " Onyx ", "Leaf5"))
        assertEquals("Onyx Leaf5", defaultDeviceName(" ", "Onyx", "Onyx Leaf5"))
    }
}
