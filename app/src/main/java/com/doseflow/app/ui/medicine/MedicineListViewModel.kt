package com.doseflow.app.ui.medicine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.domain.model.Medicine
import com.doseflow.app.domain.model.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicineWithSchedules(
    val medicine: Medicine,
    val schedules: List<Schedule>
)

data class MedicineListUiState(
    val medicines: List<MedicineWithSchedules> = emptyList(),
    val showArchived: Boolean = false,
    val isLoading: Boolean = true
)

class MedicineListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(MedicineListUiState())
    val uiState: StateFlow<MedicineListUiState> = _uiState.asStateFlow()

    init {
        loadMedicines()
    }

    private fun loadMedicines() {
        viewModelScope.launch {
            repository.getAllMedicines().collect { meds ->
                val list = meds.map { med ->
                    MedicineWithSchedules(
                        medicine = med,
                        schedules = emptyList() // we can enrich if needed or load per med
                    )
                }
                _uiState.update {
                    it.copy(medicines = list, isLoading = false)
                }
            }
        }
    }

    fun toggleArchivedFilter() {
        _uiState.update { it.copy(showArchived = !it.showArchived) }
    }

    fun archiveMedicine(id: String, archive: Boolean) {
        viewModelScope.launch {
            repository.setMedicineArchived(id, archive)
        }
    }

    fun deleteMedicine(id: String) {
        viewModelScope.launch {
            repository.deleteMedicine(id)
        }
    }
}
