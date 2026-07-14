package com.amwangfan.omnireader.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressSyncPolicyTest {
    @Test
    fun decideProgressSync_usesNewestTimestamp() {
        assertEquals(ProgressSyncAction.PullRemote, decideProgressSync(100, 200))
        assertEquals(ProgressSyncAction.PushLocal, decideProgressSync(200, 100))
        assertEquals(ProgressSyncAction.None, decideProgressSync(100, 100))
    }

    @Test
    fun decideProgressSync_handlesMissingRemoteProgress() {
        assertEquals(ProgressSyncAction.PushLocal, decideProgressSync(100, null))
        assertEquals(ProgressSyncAction.None, decideProgressSync(0, null))
    }

    @Test
    fun chapterIndexFromLocator_rejectsUnsupportedOrInvalidValues() {
        assertEquals(12, chapterIndexFromLocator("chapter:12"))
        assertNull(chapterIndexFromLocator("page:12"))
        assertNull(chapterIndexFromLocator("chapter:-1"))
        assertNull(chapterIndexFromLocator("chapter:not-a-number"))
    }
}
