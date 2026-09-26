package com.doseflow.app.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.domain.model.Medicine
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ValidationResult
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import com.doseflow.app.domain.scheduling.ScheduleValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class ScheduleFrequencyType(val label: String) {
    DAILY("Daily"),
    DAYS_OF_WEEK("Days of Week"),
    EVERY_N_DAYS("Every N Days"),
    AS_NEEDED("As Needed")
}

data class ScheduleEditorUiState(
    val medicineId: String = "",
    val medicine: Medicine? = null,
    val existingScheduleId: String? = null,
    val frequencyType: ScheduleFrequencyType = ScheduleFrequencyType.DAILY,
    val selectedDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val intervalDays: Int = 2,
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val startDate: LocalDate = LocalDate.now(),
    val hasEndDate: Boolean = false,
    val endDate: LocalDate? = LocalDate.now().plusMonths(1),
    val validationErrors: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)

class ScheduleEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DoseFlowRepository(application)
    private val _uiState = MutableStateFlow(ScheduleEditorUiState())
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    fun loadSchedule(medicineId: String) {
        _uiState.update { it.copy(medicineId = medicineId) }
        viewModelScope.launch {
            val med = repository.getMedicineById(medicineId)
            val schedules = repository.getSchedulesForMedicine(medicineId).firstOrNull() ?: emptyList()
            val existing = schedules.firstOrNull { it.isActive }

            _uiState.update { current ->
                if (existing != null) {
                    val (freq, days, interval, times) = when (val r = existing.rule) {
                        is RecurrenceRule.DailyFixedTimes -> Quadruple(
                            ScheduleFrequencyType.DAILY,
                            emptySet(),
                            1,
                            r.timesOfDay
                        )
                        is RecurrenceRule.DaysOfWeek -> Quadruple(
                            ScheduleFrequencyType.DAYS_OF_WEEK,
                            r.daysOfWeek,
                            1,
                            r.timesOfDay
                        )
                        is RecurrenceRule.EveryNDays -> Quadruple(
                            ScheduleFrequencyType.EVERY_N_DAYS,
                            emptySet(),
                            r.intervalDays,
                            r.timesOfDay
                        )
                        is RecurrenceRule.AsNeeded -> Quadruple(
                            ScheduleFrequencyType.AS_NEEDED,
                            emptySet(),
                            1,
                            emptyList()
                        )
                        else -> Quadruple(
                            ScheduleFrequencyType.DAILY,
                            emptySet(),
                            1,
                            listOf(LocalTime.of(8, 0))
                        )
                    }

                    current.copy(
                        medicine = med,
                        existingScheduleId = existing.id,
                        frequencyType = freq,
                        selectedDays = if (days.isEmpty()) setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) else days,
                        intervalDays = interval,
                        times = if (times.isEmpty()) listOf(LocalTime.of(8, 0)) else times,
                        startDate = existing.startDate,
                        hasEndDate = existing.endDate != null,
                        endDate = existing.endDate ?: LocalDate.now().plusMonths(1)
                    )
                } else {
                    current.copy(medicine = med)
                }
            }
        }
    }

    fun setFrequencyType(type: ScheduleFrequencyType) {
        _uiState.update { it.copy(frequencyType = type, validationErrors = emptyList()) }
    }

    fun toggleDay(day: DayOfWeek) {
        _uiState.update { current ->
            val updated = if (current.selectedDays.contains(day)) {
                current.selectedDays - day
            } else {
                current.selectedDays + day
            }
            current.copy(selectedDays = updated, validationErrors = emptyList())
        }
    }

    fun setIntervalDays(interval: Int) {
        if (interval > 0) {
            _uiState.update { it.copy(intervalDays = interval, validationErrors = emptyList()) }
        }
    }

    fun addTime(time: LocalTime) {
        _uiState.update { current ->
            if (!current.times.contains(time)) {
                current.copy(times = (current.times + time).sorted(), validationErrors = emptyList())
            } else {
                current
            }
        }
    }

    fun removeTime(index: Int) {
        _uiState.update { current ->
            if (current.times.size > 1) {
                current.copy(times = current.times.filterIndexed { i, _ -> i != index }, validationErrors = emptyList())
            } else {
                current
            }
        }
    }

    fun updateTime(index: Int, newTime: LocalTime) {
        _uiState.update { current ->
            val updated = current.times.toMutableList()
            if (index in updated.indices) {
                updated[index] = newTime
            }
            current.copy(times = updated.sorted(), validationErrors = emptyList())
        }
    }

    fun setHasEndDate(hasEnd: Boolean) {
        _uiState.update { it.copy(hasEndDate = hasEnd) }
    }

    fun saveSchedule() {
        val current = _uiState.value
        val scheduleId = current.existingScheduleId ?: UUID.randomUUID().toString()

        val rule: RecurrenceRule = when (current.frequencyType) {
            ScheduleFrequencyType.DAILY -> RecurrenceRule.DailyFixedTimes(current.times)
            ScheduleFrequencyType.DAYS_OF_WEEK -> RecurrenceRule.DaysOfWeek(current.selectedDays, current.times)
            ScheduleFrequencyType.EVERY_N_DAYS -> RecurrenceRule.EveryNDays(current.intervalDays, current.startDate, current.times)
            ScheduleFrequencyType.AS_NEEDED -> RecurrenceRule.AsNeeded
        }

        val schedule = Schedule(
            id = scheduleId,
            medicineId = current.medicineId,
            rule = rule,
            startDate = current.startDate,
            endDate = if (current.hasEndDate) current.endDate else null,
            timeZoneId = ZoneId.systemDefault(),
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE,
            isActive = true
        )

        val validation = ScheduleValidator.validateSchedule(schedule)
        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(validationErrors = validation.errors) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.saveSchedule(schedule)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
