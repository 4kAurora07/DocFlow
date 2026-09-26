package com.doseflow.app.ui.schedule

import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ValidationResult
import com.doseflow.app.domain.scheduling.ScheduleValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEditorValidationBehaviorTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    @Test
    fun dailySchedule_withValidTimes_passesValidation() {
        val schedule = Schedule(
            id = "sched-1",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
            startDate = today,
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertTrue(result.isValid)
    }

    @Test
    fun dailySchedule_withDuplicateTimes_failsValidation() {
        val schedule = Schedule(
            id = "sched-dup",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(8, 0))),
            startDate = today,
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("duplicate reminder times") })
    }

    @Test
    fun dailySchedule_withEmptyTimes_failsValidation() {
        val schedule = Schedule(
            id = "sched-empty-times",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(emptyList()),
            startDate = today,
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("at least one reminder time") })
    }

    @Test
    fun daysOfWeek_withNoDaysSelected_failsValidation() {
        val schedule = Schedule(
            id = "sched-no-days",
            medicineId = "med-1",
            rule = RecurrenceRule.DaysOfWeek(
                daysOfWeek = emptySet(),
                timesOfDay = listOf(LocalTime.of(9, 0))
            ),
            startDate = today,
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("select at least one day") })
    }

    @Test
    fun everyNDays_withZeroOrNegativeInterval_failsValidation() {
        val schedule = Schedule(
            id = "sched-invalid-interval",
            medicineId = "med-1",
            rule = RecurrenceRule.EveryNDays(
                intervalDays = 0,
                anchorDate = today,
                timesOfDay = listOf(LocalTime.of(12, 0))
            ),
            startDate = today,
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("greater than 0") })
    }

    @Test
    fun invertedDateRange_failsValidation() {
        val schedule = Schedule(
            id = "sched-inverted-dates",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = today.plusDays(10),
            endDate = today.plusDays(2),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("cannot be before start date") })
    }
}
