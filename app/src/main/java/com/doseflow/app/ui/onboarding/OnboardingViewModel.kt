package com.doseflow.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    val currentStep: Int = 0,
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
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

    fun nextStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(2)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
    }

    fun completeOnboarding() {
        repository.setOnboardingCompleted(true)
    }
}
