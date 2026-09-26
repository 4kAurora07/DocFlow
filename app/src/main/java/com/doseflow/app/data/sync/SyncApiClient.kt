package com.doseflow.app.data.sync

import com.doseflow.app.data.local.entity.OutboxEventEntity

sealed interface SyncResult {
    data object Success : SyncResult
    data class RetryableError(val reason: String, val statusCode: Int? = null) : SyncResult
    data class PermanentError(val reason: String, val statusCode: Int? = null) : SyncResult
}

interface SyncApiClient {
    suspend fun pushEvent(event: OutboxEventEntity): SyncResult
}
