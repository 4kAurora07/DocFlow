package com.doseflow.app.data.sync

import com.doseflow.app.data.local.dao.OutboxDao
import com.doseflow.app.data.local.entity.OutboxEventEntity
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class OutboxSyncEngineIntegrationTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val handlerOverride = AtomicReference<HttpHandler?>(null)
    private lateinit var fakeDao: FakeOutboxDao

    class FakeOutboxDao : OutboxDao {
        val storage = ConcurrentHashMap<String, OutboxEventEntity>()

        override suspend fun getPendingEvents(limit: Int): List<OutboxEventEntity> {
            return storage.values
                .filter { it.status == "PENDING" }
                .sortedBy { it.createdAtEpochMillis }
                .take(limit)
        }

        override suspend fun getEventById(id: String): OutboxEventEntity? = storage[id]

        override suspend fun getPendingEventCount(): Int = storage.values.count { it.status == "PENDING" }

        override suspend fun getAllEvents(limit: Int): List<OutboxEventEntity> {
            return storage.values.sortedByDescending { it.createdAtEpochMillis }.take(limit)
        }

        override suspend fun insertEvent(event: OutboxEventEntity) {
            storage[event.id] = event
        }

        override suspend fun updateEvent(event: OutboxEventEntity) {
            storage[event.id] = event
        }

        override suspend fun deleteEvent(id: String) {
            storage.remove(id)
        }
    }

    @Before
    fun setUp() {
        fakeDao = FakeOutboxDao()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port

        server.createContext("/api/sync/events") { exchange ->
            val custom = handlerOverride.get()
            if (custom != null) {
                custom.handle(exchange)
                return@createContext
            }

            val body = InputStreamReader(exchange.requestBody, Charsets.UTF_8).readText()
            val responseJson = """{"success":true,"processedEvents":1}"""
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        handlerOverride.set(null)
    }

    @Test
    fun drainOutbox_successfulSync_marksEventsAsSentAndTracksAttempts() = runBlocking {
        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-e2e-1",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-101",
            eventType = "DOSE_TAKEN",
            payloadJson = """{"status":"TAKEN"}""",
            createdAtEpochMillis = 1000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )
        fakeDao.insertEvent(event)

        val processed = OutboxSyncEngine.drainOutbox(fakeDao, client, currentTimeMs = 5000L)

        assertEquals("Should process 1 event", 1, processed)
        val updated = fakeDao.getEventById("evt-e2e-1")
        assertNotNull(updated)
        assertEquals("Status must transition to SENT", "SENT", updated!!.status)
        assertEquals("Attempt count must increment to 1", 1, updated.attemptCount)
        assertEquals("Last attempt time must be recorded", 5000L, updated.lastAttemptEpochMillis)
        assertEquals("No pending events remaining", 0, fakeDao.getPendingEventCount())
    }

    @Test
    fun drainOutbox_retryableFailure_incrementsAttemptAndMaintainsPending() = runBlocking {
        handlerOverride.set { exchange ->
            val resp = """{"error":"Gateway Timeout"}""".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(504, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }

        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-e2e-retry",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-102",
            eventType = "DOSE_TAKEN",
            payloadJson = """{"status":"TAKEN"}""",
            createdAtEpochMillis = 1000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )
        fakeDao.insertEvent(event)

        val processed = OutboxSyncEngine.drainOutbox(fakeDao, client, currentTimeMs = 5000L)

        assertEquals("Should attempt 1 event", 1, processed)
        val updated = fakeDao.getEventById("evt-e2e-retry")
        assertNotNull(updated)
        assertEquals("Status must remain PENDING for backoff retry", "PENDING", updated!!.status)
        assertEquals("Attempt count must increment to 1", 1, updated.attemptCount)
        assertEquals("Last attempt time must be recorded", 5000L, updated.lastAttemptEpochMillis)
        assertEquals("Pending event count must still be 1", 1, fakeDao.getPendingEventCount())
    }

    @Test
    fun drainOutbox_maxRetriesExceeded_transitionsToDeadLetter() = runBlocking {
        handlerOverride.set { exchange ->
            val resp = """{"error":"Internal Error"}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(500, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }

        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        // Event already failed 9 times; attempt 10 will trigger dead-lettering
        val event = OutboxEventEntity(
            id = "evt-e2e-exhausted",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-103",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1000L,
            attemptCount = 9,
            lastAttemptEpochMillis = 10_000L,
            status = "PENDING"
        )
        fakeDao.insertEvent(event)

        // Current time is well past required backoff
        val processed = OutboxSyncEngine.drainOutbox(fakeDao, client, currentTimeMs = 500_000L)

        assertEquals(1, processed)
        val updated = fakeDao.getEventById("evt-e2e-exhausted")
        assertNotNull(updated)
        assertEquals("Status must transition to DEAD_LETTER after 10 attempts", "DEAD_LETTER", updated!!.status)
        assertEquals("Attempt count must increment to 10", 10, updated.attemptCount)
    }

    @Test
    fun drainOutbox_permanentError_transitionsImmediatelyToFailed() = runBlocking {
        handlerOverride.set { exchange ->
            val resp = """{"error":"Patient Not Found"}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(404, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }

        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-e2e-badrequest",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-999",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )
        fakeDao.insertEvent(event)

        val processed = OutboxSyncEngine.drainOutbox(fakeDao, client, currentTimeMs = 5000L)

        assertEquals(1, processed)
        val updated = fakeDao.getEventById("evt-e2e-badrequest")
        assertNotNull(updated)
        assertEquals("Status must immediately become FAILED on permanent 4xx", "FAILED", updated!!.status)
        assertEquals(1, updated.attemptCount)
    }

    @Test
    fun drainOutbox_enforcesExponentialBackoff_skipsEventIfAttemptedRecently() = runBlocking {
        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        // Attempt 2 requires at least 4000ms backoff
        val event = OutboxEventEntity(
            id = "evt-e2e-backoff",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-104",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1000L,
            attemptCount = 2,
            lastAttemptEpochMillis = 100_000L,
            status = "PENDING"
        )
        fakeDao.insertEvent(event)

        // Only 1500ms elapsed -> must skip!
        val processed = OutboxSyncEngine.drainOutbox(fakeDao, client, currentTimeMs = 101_500L)

        assertEquals("Must skip events still inside backoff window", 0, processed)
        val updated = fakeDao.getEventById("evt-e2e-backoff")
        assertEquals(2, updated!!.attemptCount)
        assertEquals(100_000L, updated.lastAttemptEpochMillis)
    }
}
