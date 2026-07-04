package com.amwangfan.omnireader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDestinationTest {
    @Test
    fun oneTimeTreeOverridesGlobalTree() {
        assertEquals(
            StorageDestination.Tree("content://one"),
            resolveStorageDestination("content://one", "content://global"),
        )
    }

    @Test
    fun globalTreeOverridesInternalFallback() {
        assertEquals(
            StorageDestination.Tree("content://global"),
            resolveStorageDestination(null, "content://global"),
        )
    }

    @Test
    fun blankTreesUseInternalStorage() {
        assertEquals(
            StorageDestination.Internal,
            resolveStorageDestination("", ""),
        )
    }
}
