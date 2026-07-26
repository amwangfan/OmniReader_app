package com.amwangfan.omnireader.sync

enum class ProgressSyncAction {
    PullRemote,
    PushLocal,
    None,
}

fun decideProgressSync(localUpdatedAt: Long, remoteUpdatedAt: Long?): ProgressSyncAction = when {
    remoteUpdatedAt == null && localUpdatedAt > 0 -> ProgressSyncAction.PushLocal
    remoteUpdatedAt == null -> ProgressSyncAction.None
    remoteUpdatedAt > localUpdatedAt -> ProgressSyncAction.PullRemote
    localUpdatedAt > remoteUpdatedAt -> ProgressSyncAction.PushLocal
    else -> ProgressSyncAction.None
}

fun chapterIndexFromLocator(locator: String): Int? = locator
    .takeIf { it.startsWith("chapter:") }
    ?.substringAfter("chapter:")
    ?.toIntOrNull()
    ?.takeIf { it >= 0 }
