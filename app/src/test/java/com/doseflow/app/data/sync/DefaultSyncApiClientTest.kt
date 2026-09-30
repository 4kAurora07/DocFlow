package com.doseflow.app.data.sync

import com.doseflow.app.data.local.entity.OutboxEventEntity
import com.sun.net.httpserver.HttpExchange
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DefaultSyncApiClientTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val receivedRequests = mutableListOf<String>()
    private val requestCount = AtomicInteger(0)
    private val handlerOverride = AtomicReference<HttpHandler?>(null)

    @Before
    fun setUp() {
        // Start local in-memory HTTP server on random ephemeral port
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port

        server.createContext("/api/sync/events") { exchange ->
            requestCount.incrementAndGet()
            val custom = handlerOverride.get()
            if (custom != null) {
                custom.handle(exchange)
                return@createContext
            }

            // Default 200 OK handler
            val body = InputStreamReader(exchange.requestBody, Charsets.UTF_8).readText()
            receivedRequests.add(body)

            val responseJson = """{"success":true,"processedEvents":1,"appliedDoseUpdates":1}"""
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
        receivedRequests.clear()
        requestCount.set(0)
        handlerOverride.set(null)
    }

    @Test
    fun pushEvent_successfulDelivery_returnsSuccessAndTransmitsCorrectJsonPayload() = runBlocking {
        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-sync-101",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-today-01",
            eventType = "DOSE_TAKEN",
            payloadJson = """{"occurrenceId":"occ-today-01","status":"TAKEN","timestamp":1727670000000}""",
            createdAtEpochMillis = 1727670000000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val result = client.pushEvent(event)

        assertEquals("Must return SyncResult.Success on HTTP 200", SyncResult.Success, result)
        assertEquals("Server must have received exactly 1 HTTP request", 1, receivedRequests.size)

        val sentBody = receivedRequests.first()
        assertTrue("Sent body must contain patientId", sentBody.contains("patient_eleanor_001"))
        assertTrue("Sent body must contain event ID", sentBody.contains("evt-sync-101"))
        assertTrue("Sent body must contain eventType DOSE_TAKEN", sentBody.contains("DOSE_TAKEN"))
        assertTrue("Sent body must contain aggregateId occ-today-01", sentBody.contains("occ-today-01"))
    }

    @Test
    fun pushEvent_serverReturns503_returnsRetryableErrorWithStatusCode() = runBlocking {
        handlerOverride.set { exchange ->
            val errorResponse = """{"error":"Service temporarily unavailable"}""".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(503, errorResponse.size.toLong())
            exchange.responseBody.use { it.write(errorResponse) }
        }

        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-retryable-1",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-1",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1727670000000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val result = client.pushEvent(event)

        assertTrue("Must be classified as RetryableError", result is SyncResult.RetryableError)
        val retryable = result as SyncResult.RetryableError
        assertEquals(503, retryable.statusCode)
        assertTrue(retryable.reason.contains("503"))
    }

    @Test
    fun pushEvent_serverReturns400_returnsPermanentErrorWithStatusCode() = runBlocking {
        handlerOverride.set { exchange ->
            val errorResponse = """{"error":"Bad Request: Invalid event schema"}""".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(400, errorResponse.size.toLong())
            exchange.responseBody.use { it.write(errorResponse) }
        }

        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-permanent-1",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-1",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1727670000000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val result = client.pushEvent(event)

        assertTrue("Must be classified as PermanentError", result is SyncResult.PermanentError)
        val permanent = result as SyncResult.PermanentError
        assertEquals(400, permanent.statusCode)
        assertTrue(permanent.reason.contains("400"))
    }

    @Test
    fun pushEvent_connectionRefused_returnsRetryableError() = runBlocking {
        // Point client to non-existent server / closed port
        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:59999",
            isNetworkConnected = { true }
        )

        val event = OutboxEventEntity(
            id = "evt-conn-refused",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-1",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1727670000000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val result = client.pushEvent(event)

        assertTrue("Network connection refusal must produce RetryableError", result is SyncResult.RetryableError)
        val retryable = result as SyncResult.RetryableError
        assertTrue(retryable.reason.contains("Network exception"))
    }

    @Test
    fun pushEvent_deviceOffline_shortCircuitsWithoutNetworkCall() = runBlocking {
        val client = DefaultSyncApiClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            isNetworkConnected = { false } // Simulated device offline
        )

        val event = OutboxEventEntity(
            id = "evt-offline",
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = "occ-1",
            eventType = "DOSE_TAKEN",
            payloadJson = "{}",
            createdAtEpochMillis = 1727670000000L,
            attemptCount = 0,
            lastAttemptEpochMillis = null,
            status = "PENDING"
        )

        val result = client.pushEvent(event)

        assertTrue("Offline device must yield RetryableError", result is SyncResult.RetryableError)
        val retryable = result as SyncResult.RetryableError
        assertTrue(retryable.reason.contains("Device offline"))
        assertEquals("Server must NOT receive any HTTP request while offline", 0, requestCount.get())
    }
}
