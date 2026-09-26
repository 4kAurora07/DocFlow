package com.doseflow.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

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

        _uiState.update {
            it.copy(
                hasExactAlarmPermission = exactAlarm,
                hasNotificationPermission = notifications,
                isIgnoringBatteryOptimizations = battery
            )
        }
    }
}
