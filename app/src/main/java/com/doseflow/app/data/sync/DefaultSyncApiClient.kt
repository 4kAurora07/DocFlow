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

    fun getResolvedBaseUrl(): String {
        if (context != null) {
            val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("sync_server_url", null)
            if (!saved.isNullOrBlank() && !saved.contains("10.1.1.") && !saved.contains("10.0.2.") && !saved.contains("localhost")) {
                return saved.trimEnd('/')
            }
        }
        return DEFAULT_BASE_URL.trimEnd('/')
    }

    private fun getResolvedPatientId(): String {
        if (context != null) {
            val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
            var pId = prefs.getString("patient_id", null)
            if (pId.isNullOrBlank()) {
                pId = "patient_" + java.util.UUID.randomUUID().toString().substring(0, 8)
                prefs.edit().putString("patient_id", pId).apply()
            }
            return pId
        }
        return "patient_app_user"
    }

    override suspend fun pushEvent(event: OutboxEventEntity): SyncResult = withContext(Dispatchers.IO) {
        if (!checkNetwork()) {
            Log.d(TAG, "Device is offline. Event ${event.id} postponed.")
            return@withContext SyncResult.RetryableError("Device offline; no network connection available")
        }

        var connection: HttpURLConnection? = null
        try {
            val activeBase = getResolvedBaseUrl()
            val patientId = getResolvedPatientId()
            val url = URL("$activeBase/api/sync/events")
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
                  "patientId": "${escapeJsonString(patientId)}",
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

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val activeBase = getResolvedBaseUrl()
        try {
            val url = URL("$activeBase/api/dashboard")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val code = connection.responseCode
            if (code in 200..499) {
                Pair(true, "Connected to $activeBase (HTTP $code)")
            } else {
                Pair(false, "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            Pair(false, "Cannot connect to $activeBase: ${e.localizedMessage ?: e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun generateCaregiverCode(
        patientId: String = getResolvedPatientId(),
        patientName: String = "Patient"
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val activeBase = getResolvedBaseUrl()
            val url = URL("$activeBase/api/access-codes/generate")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            val jsonBody = """{"patientId":"${escapeJsonString(patientId)}","patientName":"${escapeJsonString(patientName)}"}"""
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val body = readStream(connection, responseCode)
                val codeRegex = """"code"\s*:\s*"([^"]+)"""".toRegex()
                codeRegex.find(body)?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error generating caregiver code: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun syncPatientState(jsonPayload: String): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val activeBase = getResolvedBaseUrl()
            val url = URL("$activeBase/api/sync/patient-state")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(jsonPayload) }
            connection.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing patient state: ${e.message}")
            false
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
        const val DEFAULT_BASE_URL = "https://adhivio-med.onrender.com"
        const val CONNECT_TIMEOUT_MS = 30000
        const val READ_TIMEOUT_MS = 30000
    }
}
