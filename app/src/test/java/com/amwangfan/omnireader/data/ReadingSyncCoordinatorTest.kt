package com.amwangfan.omnireader.data

import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSyncCoordinatorTest {
    @Test fun dirtyState_registersBeforeUploadingAndBecomesClean() = runTest {
        val events = mutableListOf<String>()
        val gateway = FakeGateway(events)
        val store = ReadingStateStore(tempDir())
        store.put("b", "d", ReadingStateRecord(locator(), mapOf("2026-07-06" to 5), true))
        val coordinator = ReadingSyncCoordinator(gateway, store, identity())
        coordinator.syncBook("b")
        assertEquals(listOf("register", "put", "get"), events)
        assertFalse(store.get("b", "d")!!.dirty)
        assertEquals("server", store.get("b", "d")!!.lastServerUpdatedAt)
    }

    @Test fun failedUpload_remainsDirty() = runTest {
        val store = ReadingStateStore(tempDir()); store.put("b", "d", ReadingStateRecord(locator(), dirty=true))
        val gateway = FakeGateway(mutableListOf()).apply { failPut = true }
        runCatching { ReadingSyncCoordinator(gateway, store, identity()).syncBook("b") }
        assertTrue(store.get("b", "d")!!.dirty)
    }

    @Test fun cleanState_usesOtherDeviceGlobalOnlyForResume() = runTest {
        val store = ReadingStateStore(tempDir()); store.put("b", "d", ReadingStateRecord(locator("local"), dirty=false))
        val gateway = FakeGateway(mutableListOf()).apply {
            response = ProgressResponse(null, ProgressDto("b", "other", "Other Reader", locator("global"), .8, null, "new", false), "r")
        }
        val result = ReadingSyncCoordinator(gateway, store, identity()).resumeFor("b")
        assertEquals("global", result!!.locator.chapterHref)
        assertEquals("Other Reader", result.sourceDeviceName)
        assertEquals("local", store.get("b", "d")!!.locator.chapterHref)
        assertFalse(store.get("b", "d")!!.dirty)
    }

    @Test fun uploadCompletion_doesNotCleanProgressCreatedDuringRequest() = runTest {
        val store = ReadingStateStore(tempDir()); store.put("b", "d", ReadingStateRecord(locator(), dirty=true, generation=1))
        val gateway = FakeGateway(mutableListOf()).apply {
            onPut = { store.mergeElapsed("b", "d", emptyMap(), locator("newer")) }
        }
        ReadingSyncCoordinator(gateway, store, identity()).syncBook("b")
        assertTrue(store.get("b", "d")!!.dirty)
        assertEquals("newer", store.get("b", "d")!!.locator.chapterHref)
    }

    @Test fun lateGlobalResponse_cannotOverrideLocalReadingStartedWhileWaiting() = runTest {
        val store = ReadingStateStore(tempDir()); store.put("b", "d", ReadingStateRecord(locator("local"), dirty=false, generation=1))
        val gateway = FakeGateway(mutableListOf()).apply {
            onGet = { store.mergeElapsed("b", "d", emptyMap(), locator("new-local")) }
            response = ProgressResponse(null, ProgressDto("b", "other", "Other", locator("global"), .8, null, "server", false), "r")
        }
        val result = ReadingSyncCoordinator(gateway, store, identity()).resumeFor("b")
        assertEquals("new-local", result!!.locator.chapterHref)
        assertEquals(null, result.sourceDeviceName)
    }

    private class FakeGateway(private val events: MutableList<String>) : ReadingSyncGateway {
        var failPut=false
        var onPut: (() -> Unit)? = null
        var onGet: (() -> Unit)? = null
        var response=ProgressResponse(null, null, "r")
        override suspend fun register(identity: DeviceRegistrationRequest) { events += "register" }
        override suspend fun get(bookId: String, deviceId: String): ProgressResponse { events += "get"; onGet?.invoke(); return response }
        override suspend fun put(bookId: String, request: ProgressPutRequest): ProgressResponse {
            events += "put"; if (failPut) error("offline"); onPut?.invoke()
            return ProgressResponse(ProgressDto(bookId, request.deviceId, "This", request.locator, request.percentage, request.clientUpdatedAt, "server", false), null, "r")
        }
    }
    private fun identity()=DeviceRegistrationRequest("d","This","This",manufacturer="Onyx",model="Leaf",appVersion=".3")
    private fun locator(href:String="c")=ReadingLocator(contentRevision="r",chapterHref=href,chapterIndex=0,blockIndex=0)
    private fun tempDir()=createTempDirectory("coordinator-").toFile()
}
