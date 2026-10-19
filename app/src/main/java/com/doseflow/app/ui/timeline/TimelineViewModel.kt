package com.doseflow.app.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.data.DoseWithMedicine
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    val doses: List<DoseWithMedicine> = emptyList(),
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val isLoading: Boolean = true,
    val caregiverCode: String? = null
)

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(TimelineUiState(caregiverCode = repository.getCaregiverCode()))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        observeDoses()
        viewModelScope.launch {
            try {
                val code = repository.ensureCaregiverCodeAndSync()
                val activeCode = code ?: repository.getCaregiverCode()
                _uiState.update { it.copy(caregiverCode = activeCode) }
            } catch (_: Exception) {}
        }
    }

    fun checkPermissions() {
        val app = getApplication<Application>()
        val exactAlarm = PowerAndPermissionHelper.canScheduleExactAlarms(app)
        val notifications = PowerAndPermissionHelper.hasNotificationPermission(app)
        val ignoringBattery = PowerAndPermissionHelper.isIgnoringBatteryOptimizations(app)

        _uiState.update {
            it.copy(
                hasExactAlarmPermission = exactAlarm,
                hasNotificationPermission = notifications,
                isIgnoringBatteryOptimizations = ignoringBattery
            )
        }
    }

    private fun observeDoses() {
        viewModelScope.launch {
            repository.getTodayDoses().collect { doseList ->
                _uiState.update {
                    it.copy(doses = doseList, isLoading = false)
                }
            }
        }
    }

    fun markTaken(occurrenceId: String) {
        viewModelScope.launch {
            repository.markTaken(occurrenceId)
        }
    }

    fun markSkipped(occurrenceId: String) {
        viewModelScope.launch {
            repository.markSkipped(occurrenceId)
        }
    }

    fun snooze(occurrenceId: String) {
        viewModelScope.launch {
            repository.snoozeDose(occurrenceId)
        }
    }

    fun triggerAutoSync() {
        viewModelScope.launch {
            try {
                val code = repository.getCaregiverCode()
                if (code != null) {
                    _uiState.update { it.copy(caregiverCode = code) }
                }
                repository.syncFullStateToCaregiverPortal()
            } catch (_: Exception) {}
        }
    }
}
