package com.amwangfan.omnireader.data

interface ReadingSyncGateway {
    suspend fun register(identity: DeviceRegistrationRequest)
    suspend fun get(bookId: String, deviceId: String): ProgressResponse
    suspend fun put(bookId: String, request: ProgressPutRequest): ProgressResponse
}

class OmniReadingSyncGateway(
    private val api: OmniApi,
    private val baseUrl: String,
    private val token: String,
) : ReadingSyncGateway {
    override suspend fun register(identity: DeviceRegistrationRequest) { api.registerDevice(baseUrl, token, identity) }
    override suspend fun get(bookId: String, deviceId: String) = api.getProgress(baseUrl, token, bookId, deviceId)
    override suspend fun put(bookId: String, request: ProgressPutRequest) = api.putProgress(baseUrl, token, bookId, request)
}

data class ResumeProgress(
    val locator: ReadingLocator,
    val sourceDeviceName: String? = null,
    val revisionMismatch: Boolean = false,
)

class ReadingSyncCoordinator(
    private val gateway: ReadingSyncGateway,
    private val store: ReadingStateStore,
    private val identity: DeviceRegistrationRequest,
    private val baseUrl: String,
    private val token: String,
) {
    suspend fun syncBook(bookId: String): ProgressResponse {
        gateway.register(identity)
        val local = store.get(bookId, identity.id)
        if (local?.dirty == true) {
            val uploaded = gateway.put(
                bookId,
                ProgressPutRequest(
                    deviceId = identity.id,
                    locator = local.locator,
                    percentage = local.locator.bookProgress,
                    clientUpdatedAt = null,
                    dailyReadSeconds = local.dailyReadSeconds,
                ),
            )
            val accepted = uploaded.deviceProgress?.updatedAt ?: uploaded.globalProgress
                ?.takeIf { it.deviceId == identity.id }?.updatedAt
            store.markClean(bookId, identity.id, accepted)
        }
        return gateway.get(bookId, identity.id)
    }

    suspend fun syncAllDirty() {
        gateway.register(identity)
        store.dirtyRecords().filter { it.second == identity.id }.forEach { (bookId, _, _) -> syncBook(bookId) }
    }

    suspend fun resumeFor(bookId: String): ResumeProgress? {
        val local = store.get(bookId, identity.id)
        if (local?.dirty == true) {
            return runCatching { syncBook(bookId) }.getOrNull()?.bestResume(local.locator) ?: ResumeProgress(local.locator)
        }
        val response = runCatching {
            gateway.register(identity)
            gateway.get(bookId, identity.id)
        }.getOrNull() ?: return local?.let { ResumeProgress(it.locator) }
        return response.bestResume(local?.locator)
    }

    private fun ProgressResponse.bestResume(local: ReadingLocator?): ResumeProgress? {
        val best = globalProgress ?: deviceProgress
        if (best != null) return ResumeProgress(
            locator = best.locator,
            sourceDeviceName = best.deviceName.takeIf { best.deviceId != identity.id && it.isNotBlank() },
            revisionMismatch = best.revisionMismatch,
        )
        return local?.let { ResumeProgress(it) }
    }
}
