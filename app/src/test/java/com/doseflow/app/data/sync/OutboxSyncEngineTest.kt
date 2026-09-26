package com.doseflow.app.data.sync

import com.doseflow.app.data.local.entity.OutboxEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxSyncEngineTest {

    @Test
    fun calculateBackoffDelay_scalesExponentiallyAndCapsAtMaxDelay() {
        // Base delay = 2000ms, max delay = 300,000ms (5 mins)
        val delay0 = OutboxSyncEngine.calculateBackoffDelay(0, jitterFactor = 0.0)
        val delay1 = OutboxSyncEngine.calculateBackoffDelay(1, jitterFactor = 0.0)
        val delay2 = OutboxSyncEngine.calculateBackoffDelay(2, jitterFactor = 0.0)
        val delay3 = OutboxSyncEngine.calculateBackoffDelay(3, jitterFactor = 0.0)
        val delay4 = OutboxSyncEngine.calculateBackoffDelay(4, jitterFactor = 0.0)
        val delay5 = OutboxSyncEngine.calculateBackoffDelay(5, jitterFactor = 0.0)
        val delay10 = OutboxSyncEngine.calculateBackoffDelay(10, jitterFactor = 0.0)
        val delay15 = OutboxSyncEngine.calculateBackoffDelay(15, jitterFactor = 0.0)

        assertEquals("Attempt 0 has 0 delay", 0L, delay0)
        assertEquals("Attempt 1 has 2000ms delay", 2000L, delay1)
        assertEquals("Attempt 2 has 4000ms delay", 4000L, delay2)
        assertEquals("Attempt 3 has 8000ms delay", 8000L, delay3)
        assertEquals("Attempt 4 has 16000ms delay", 16000L, delay4)
        assertEquals("Attempt 5 has 32000ms delay", 32000L, delay5)

        // Attempt 10: 2000 * 2^9 = 1,024,000ms -> capped at 300,000ms
        assertEquals("Attempt 10 capped at 300,000ms", 300_000L, delay10)
        assertEquals("Attempt 15 capped at 300,000ms", 300_000L, delay15)
    }

    @Test
    fun isBackoffSatisfied_respectsElapsedTimeSinceLastAttempt() {
        val lastAttemptTime = 1_000_000L
        val event = OutboxEventEntity(
            id = "event-test-1",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-1",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 900_000L,
            attemptCount = 2, // delay for attempt 2 is 4000ms
            lastAttemptEpochMillis = lastAttemptTime,
            status = "PENDING"
        )

        // 2000ms elapsed < 4000ms required backoff -> should NOT be satisfied
        val tooEarly = OutboxSyncEngine.isBackoffSatisfied(event, currentTimeMs = lastAttemptTime + 2000L)
        assertFalse(tooEarly)

        // Exactly 4000ms elapsed -> satisfied
        val onTime = OutboxSyncEngine.isBackoffSatisfied(event, currentTimeMs = lastAttemptTime + 4000L)
        assertTrue(onTime)

        // 10,000ms elapsed -> satisfied
        val wellPast = OutboxSyncEngine.isBackoffSatisfied(event, currentTimeMs = lastAttemptTime + 10_000L)
        assertTrue(wellPast)
    }

    @Test
    fun isBackoffSatisfied_firstAttemptNeverBlocked() {
        val event = OutboxEventEntity(
            id = "event-fresh",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-fresh",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1_000_000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val satisfied = OutboxSyncEngine.isBackoffSatisfied(event, currentTimeMs = 1_000_000L)
        assertTrue("Fresh events with 0 attempts must be immediately satisfied for delivery", satisfied)
    }

    @Test
    fun syncResult_classificationDistinguishesRetryableFromPermanentErrors() {
        val success = SyncResult.Success
        val retryable = SyncResult.RetryableError("Connection timed out", statusCode = 503)
        val permanent = SyncResult.PermanentError("Bad Request: Schema mismatch", statusCode = 400)

        assertEquals(SyncResult.Success, success)
        assertEquals(503, retryable.statusCode)
        assertEquals("Connection timed out", retryable.reason)
        assertEquals(400, permanent.statusCode)
        assertEquals("Bad Request: Schema mismatch", permanent.reason)
    }
}
