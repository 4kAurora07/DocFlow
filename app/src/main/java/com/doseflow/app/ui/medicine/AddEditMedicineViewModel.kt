package com.doseflow.app.ui.medicine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.domain.model.Medicine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class AddEditMedicineUiState(
    val medicineId: String? = null,
    val name: String = "",
    val dosage: String = "",
    val unit: String = "mg",
    val instructions: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

class AddEditMedicineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(AddEditMedicineUiState())
    val uiState: StateFlow<AddEditMedicineUiState> = _uiState.asStateFlow()

    fun loadMedicine(id: String?) {
        if (id == null || id.isBlank() || id == "new") return
        viewModelScope.launch {
            val med = repository.getMedicineById(id)
            if (med != null) {
                _uiState.update {
                    it.copy(
                        medicineId = med.id,
                        name = med.name,
                        dosage = med.dosage,
                        unit = if (med.unit.isNotBlank()) med.unit else "mg",
                        instructions = med.instructions
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onDosageChange(dosage: String) {
        _uiState.update { it.copy(dosage = dosage) }
    }

    fun onUnitChange(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }

    fun onInstructionsChange(instructions: String) {
        _uiState.update { it.copy(instructions = instructions) }
    }

    fun saveMedicine() {
        val current = _uiState.value
        if (current.name.trim().isBlank()) {
            _uiState.update { it.copy(errorMessage = "Medicine name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val medId = current.medicineId ?: UUID.randomUUID().toString()
            val medicine = Medicine(
                id = medId,
                name = current.name.trim(),
                dosage = current.dosage.trim(),
                unit = current.unit.trim(),
                instructions = current.instructions.trim(),
                updatedAt = Instant.now()
            )
            repository.saveMedicine(medicine)
            _uiState.update { it.copy(isSaving = false, isSaved = true, medicineId = medId) }
        }
    }
}
