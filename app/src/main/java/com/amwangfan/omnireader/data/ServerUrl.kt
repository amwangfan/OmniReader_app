package com.amwangfan.omnireader.data

fun normalizeServerBaseUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    require(trimmed.isNotBlank()) { "Server address is required" }
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "Server address must start with http:// or https://"
    }
    return trimmed
}
