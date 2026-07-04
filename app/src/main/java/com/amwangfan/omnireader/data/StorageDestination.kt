package com.amwangfan.omnireader.data

sealed interface StorageDestination {
    data object Internal : StorageDestination

    data class Tree(val uri: String) : StorageDestination
}

fun resolveStorageDestination(
    oneTimeTreeUri: String?,
    defaultTreeUri: String?,
): StorageDestination =
    oneTimeTreeUri?.takeIf(String::isNotBlank)?.let(StorageDestination::Tree)
        ?: defaultTreeUri?.takeIf(String::isNotBlank)?.let(StorageDestination::Tree)
        ?: StorageDestination.Internal
