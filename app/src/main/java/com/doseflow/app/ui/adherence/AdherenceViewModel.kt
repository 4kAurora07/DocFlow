package com.doseflow.app.ui.adherence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.AdherenceSummary
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.data.DoseWithMedicine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdherenceUiState(
    val selectedPeriodDays: Int = 7,
    val summary: AdherenceSummary = AdherenceSummary(
        periodDays = 7,
        totalScheduledDoses = 0,
        takenDoses = 0,
        skippedDoses = 0,
        missedDoses = 0,
        pendingDoses = 0,
        adherencePercentage = 100.0
    ),
    val historyList: List<DoseWithMedicine> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdherenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val periodDaysFlow = MutableStateFlow(7)
    private val _uiState = MutableStateFlow(AdherenceUiState())
    val uiState: StateFlow<AdherenceUiState> = _uiState.asStateFlow()

    init {
        observeAdherence()
        observeHistory()
    }

    private fun observeAdherence() {
        viewModelScope.launch {
            periodDaysFlow.flatMapLatest { days ->
                repository.getAdherenceSummary(days)
            }.collect { summary ->
                _uiState.update {
                    it.copy(summary = summary, isLoading = false)
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getRecentHistory(50).collect { list ->
                _uiState.update {
                    it.copy(historyList = list)
                }
            }
        }
    }

    fun setPeriod(days: Int) {
        periodDaysFlow.value = days
        _uiState.update { it.copy(selectedPeriodDays = days) }
    }
}
