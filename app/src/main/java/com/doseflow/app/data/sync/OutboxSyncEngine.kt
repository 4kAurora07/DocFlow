package com.doseflow.app.data.sync

import android.content.Context
import android.util.Log
import com.doseflow.app.data.local.AppDatabase
import com.doseflow.app.data.local.entity.OutboxEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object OutboxSyncEngine {

    private const val TAG = "OutboxSyncEngine"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private val isSyncing = AtomicBoolean(false)

    const val MAX_RETRY_ATTEMPTS = 10
    const val BASE_DELAY_MS = 2000L      // 2 seconds
    const val MAX_DELAY_MS = 300_000L    // 5 minutes (300 seconds)

    /**
     * Enqueues an outbox event synchronously into local Room SQLite.
     * Guaranteed never to execute any network calls in this thread.
     */
    suspend fun enqueueEvent(
        db: AppDatabase,
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payloadJson: String
    ): OutboxEventEntity {
        val now = System.currentTimeMillis()
        val event = OutboxEventEntity(
            id = UUID.randomUUID().toString(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payloadJson = payloadJson,
            createdAtEpochMillis = now,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )
        db.outboxDao().insertEvent(event)
        Log.d(TAG, "Enqueued outbox event ${event.id} ($eventType for $aggregateId)")
        return event
    }

    /**
     * Triggers outbox synchronization in a completely decoupled, fire-and-forget background coroutine.
     */
    fun triggerSync(
        context: Context,
        apiClient: SyncApiClient? = null
    ) {
        val client = apiClient ?: DefaultSyncApiClient(context.applicationContext)
        val db = AppDatabase.getInstance(context.applicationContext)

        syncScope.launch {
            drainOutbox(db, client)
        }
    }

    /**
     * Synchronously processes pending events. Safe for direct invocation from workers or unit tests.
     */
    suspend fun drainOutbox(
        db: AppDatabase,
        client: SyncApiClient,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Int {
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "Sync already in progress; skipping duplicate trigger.")
            return 0
        }

        try {
            isSyncing.set(true)
            val pendingEvents = db.outboxDao().getPendingEvents(limit = 50)
            if (pendingEvents.isEmpty()) {
                Log.d(TAG, "Outbox is clear. No pending events to sync.")
                return 0
            }

            var processedCount = 0
            for (event in pendingEvents) {
                // Check exponential backoff window
                if (!isBackoffSatisfied(event, currentTimeMs)) {
                    Log.d(TAG, "Event ${event.id} still in backoff window (attempts: ${event.attemptCount}). Skipping.")
                    continue
                }

                processedCount++
                val nextAttempt = event.attemptCount + 1
                val result = client.pushEvent(event)

                when (result) {
                    is SyncResult.Success -> {
                        val updated = event.copy(
                            status = "SENT",
                            attemptCount = nextAttempt,
                            lastAttemptEpochMillis = currentTimeMs
                        )
                        db.outboxDao().updateEvent(updated)
                        Log.i(TAG, "Outbox event ${event.id} successfully synced.")
                    }
                    is SyncResult.RetryableError -> {
                        val nextStatus = if (nextAttempt >= MAX_RETRY_ATTEMPTS) "DEAD_LETTER" else "PENDING"
                        val updated = event.copy(
                            status = nextStatus,
                            attemptCount = nextAttempt,
                            lastAttemptEpochMillis = currentTimeMs
                        )
                        db.outboxDao().updateEvent(updated)
                        Log.w(TAG, "Outbox event ${event.id} failed (attempt $nextAttempt/$MAX_RETRY_ATTEMPTS): ${result.reason}")
                    }
                    is SyncResult.PermanentError -> {
                        val updated = event.copy(
                            status = "FAILED",
                            attemptCount = nextAttempt,
                            lastAttemptEpochMillis = currentTimeMs
                        )
                        db.outboxDao().updateEvent(updated)
                        Log.e(TAG, "Outbox event ${event.id} permanently rejected: ${result.reason}")
                    }
                }
            }
            return processedCount
        } finally {
            isSyncing.set(false)
            syncMutex.unlock()
        }
    }

    /**
     * Determines whether enough backoff delay has elapsed since the last failed attempt.
     */
    fun isBackoffSatisfied(event: OutboxEventEntity, currentTimeMs: Long): Boolean {
        if (event.lastAttemptEpochMillis == null || event.attemptCount == 0) return true
        val elapsed = currentTimeMs - event.lastAttemptEpochMillis
        val requiredBackoff = calculateBackoffDelay(event.attemptCount)
        return elapsed >= requiredBackoff
    }

    /**
     * Calculates exponential backoff: min(MAX_DELAY, BASE_DELAY * 2^(attemptCount - 1)) + jitter.
     */
    fun calculateBackoffDelay(
        attemptCount: Int,
        baseDelayMs: Long = BASE_DELAY_MS,
        maxDelayMs: Long = MAX_DELAY_MS,
        jitterFactor: Double = 0.1
    ): Long {
        if (attemptCount <= 0) return 0L
        val exponential = (2.0.pow((attemptCount - 1).coerceAtMost(30).toDouble()) * baseDelayMs).toLong()
        val capped = min(maxDelayMs, exponential)
        val jitter = if (jitterFactor > 0.0) {
            (capped * jitterFactor * Random.nextDouble(-1.0, 1.0)).toLong()
        } else 0L
        return (capped + jitter).coerceAtLeast(0L)
    }
}
