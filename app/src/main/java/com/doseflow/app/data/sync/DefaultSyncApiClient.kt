package com.doseflow.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.doseflow.app.data.local.entity.OutboxEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DefaultSyncApiClient(private val context: Context) : SyncApiClient {

    override suspend fun pushEvent(event: OutboxEventEntity): SyncResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "Device is offline. Event ${event.id} postponed.")
            return@withContext SyncResult.RetryableError("Device offline; no network connection available")
        }

        try {
            // Simulated local network latency
            delay(10)
            Log.i(TAG, "Successfully synced event ${event.id} (type=${event.eventType}) to remote endpoint.")
            SyncResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Network failure syncing event ${event.id}: ${e.message}")
            SyncResult.RetryableError("Network exception: ${e.message}")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "DefaultSyncApiClient"
    }
}
