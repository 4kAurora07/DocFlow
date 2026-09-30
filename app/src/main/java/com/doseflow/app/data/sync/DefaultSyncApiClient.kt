package com.doseflow.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.doseflow.app.data.local.entity.OutboxEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DefaultSyncApiClient(
    private val context: Context? = null,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val isNetworkConnected: (() -> Boolean)? = null
) : SyncApiClient {

    override suspend fun pushEvent(event: OutboxEventEntity): SyncResult = withContext(Dispatchers.IO) {
        if (!checkNetwork()) {
            Log.d(TAG, "Device is offline. Event ${event.id} postponed.")
            return@withContext SyncResult.RetryableError("Device offline; no network connection available")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/sync/events")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            // Construct well-formed JSON payload
            val escapedPayload = escapeJsonString(event.payloadJson)
            val jsonBody = """
                {
                  "patientId": "patient_eleanor_001",
                  "events": [
                    {
                      "id": "${escapeJsonString(event.id)}",
                      "eventType": "${escapeJsonString(event.eventType)}",
                      "aggregateType": "${escapeJsonString(event.aggregateType)}",
                      "aggregateId": "${escapeJsonString(event.aggregateId)}",
                      "payloadJson": "$escapedPayload"
                    }
                  ]
                }
            """.trimIndent()

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = readStream(connection, responseCode)

            when (responseCode) {
                in 200..299 -> {
                    Log.i(TAG, "Successfully synced event ${event.id} (HTTP $responseCode)")
                    SyncResult.Success
                }
                in 400..499 -> {
                    Log.e(TAG, "Permanent client error syncing event ${event.id}: HTTP $responseCode - $responseBody")
                    SyncResult.PermanentError("HTTP $responseCode: $responseBody", statusCode = responseCode)
                }
                else -> {
                    Log.w(TAG, "Retryable server error syncing event ${event.id}: HTTP $responseCode - $responseBody")
                    SyncResult.RetryableError("HTTP $responseCode: $responseBody", statusCode = responseCode)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception syncing event ${event.id}: ${e.message}")
            SyncResult.RetryableError("Network exception: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun checkNetwork(): Boolean {
        isNetworkConnected?.let { return it.invoke() }
        val ctx = context ?: return true
        val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun readStream(conn: HttpURLConnection, responseCode: Int): String {
        return try {
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            if (stream == null) "" else BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun escapeJsonString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private const val TAG = "DefaultSyncApiClient"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
    }
}
