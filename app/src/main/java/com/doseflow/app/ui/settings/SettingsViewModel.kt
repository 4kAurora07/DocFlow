package com.doseflow.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val canDrawOverlays: Boolean = true,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val caregiverCode: String? = null,
    val serverUrl: String = "https://adhivio-med.onrender.com",
    val patientName: String = "My Android Device",
    val homeScreenPopupEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val connectionSuccess: Boolean? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val app = getApplication<Application>()
        val exactAlarm = PowerAndPermissionHelper.canScheduleExactAlarms(app)
        val notifications = PowerAndPermissionHelper.hasNotificationPermission(app)
        val battery = PowerAndPermissionHelper.isIgnoringBatteryOptimizations(app)
        val overlay = PowerAndPermissionHelper.canDrawOverlays(app)
        val code = repository.getCaregiverCode()
        val url = repository.getServerUrl()
        val name = repository.getPatientName()
        val popupEnabled = repository.isHomeScreenPopupEnabled()

        _uiState.update {
            it.copy(
                hasExactAlarmPermission = exactAlarm,
                hasNotificationPermission = notifications,
                isIgnoringBatteryOptimizations = battery,
                canDrawOverlays = overlay,
                caregiverCode = code,
                serverUrl = url,
                patientName = name,
                homeScreenPopupEnabled = popupEnabled
            )
        }
    }

    fun updateServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        repository.setServerUrl(clean)
        _uiState.update { it.copy(serverUrl = clean, connectionSuccess = null) }
    }

    fun updatePatientName(name: String) {
        val clean = name.trim()
        repository.setPatientName(clean)
        _uiState.update { it.copy(patientName = clean) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Testing server connection...") }
            val (ok, message) = repository.testServerConnection()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    connectionSuccess = ok,
                    syncStatusMessage = message
                )
            }
        }
    }

    fun generateCaregiverCode(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Connecting to server and generating code...") }
            val code = repository.generateAndRegisterCaregiverCode()
            if (code != null) {
                repository.setCaregiverCode(code)
                _uiState.update {
                    it.copy(
                        caregiverCode = code,
                        isSyncing = false,
                        connectionSuccess = true,
                        syncStatusMessage = "Connected! Patient data synced to web dashboard."
                    )
                }
                onComplete?.invoke(code)
            } else {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        connectionSuccess = false,
                        syncStatusMessage = "Cloud server is waking up. Please wait a moment and tap again."
                    )
                }
            }
        }
    }

    fun syncDataNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Syncing with cloud portal...") }
            val success = repository.syncFullStateToCaregiverPortal()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    connectionSuccess = success,
                    syncStatusMessage = if (success) "Successfully synced real medication records to portal!" else "Syncing with cloud server. Please tap once more."
                )
            }
        }
    }

    fun setHomeScreenPopupEnabled(enabled: Boolean) {
        repository.setHomeScreenPopupEnabled(enabled)
        _uiState.update { it.copy(homeScreenPopupEnabled = enabled) }
    }
}

