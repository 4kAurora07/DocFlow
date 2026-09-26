package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ValidationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleValidatorTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun validSchedule_passesValidation() {
        val schedule = Schedule(
            id = "valid-sched",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertTrue(result.isValid)
    }

    @Test
    fun blankIds_failsValidation() {
        val schedule = Schedule(
            id = "   ",
            medicineId = "",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Schedule ID") })
        assertTrue(errors.any { it.contains("Medicine ID") })
    }

    @Test
    fun endDateBeforeStartDate_failsValidation() {
        val schedule = Schedule(
            id = "sched-inverted-dates",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 5, 10),
            endDate = LocalDate.of(2026, 5, 5),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("cannot be before start date") })
    }

    @Test
    fun emptyFixedTimes_failsValidation() {
        val schedule = Schedule(
            id = "sched-empty-times",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(emptyList()),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("at least one reminder time") })
    }

    @Test
    fun duplicateFixedTimes_failsValidation() {
        val schedule = Schedule(
            id = "sched-dup-times",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("duplicate reminder times") })
    }

    @Test
    fun emptyDaysOfWeek_failsValidation() {
        val schedule = Schedule(
            id = "sched-empty-dow",
            medicineId = "med-1",
            rule = RecurrenceRule.DaysOfWeek(
                daysOfWeek = emptySet(),
                timesOfDay = listOf(LocalTime.of(9, 0))
            ),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("select at least one day") })
    }

    @Test
    fun invalidHourlyInterval_nonPositive_failsValidation() {
        val schedule = Schedule(
            id = "sched-bad-interval",
            medicineId = "med-1",
            rule = RecurrenceRule.HourlyInterval(
                intervalHours = 0,
                anchorInstant = Instant.now()
            ),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Hourly interval must be greater than 0") })
    }

    @Test
    fun invalidEveryNDays_nonPositive_failsValidation() {
        val schedule = Schedule(
            id = "sched-bad-every-n",
            medicineId = "med-1",
            rule = RecurrenceRule.EveryNDays(
                intervalDays = -2,
                anchorDate = LocalDate.of(2026, 1, 1),
                timesOfDay = listOf(LocalTime.of(8, 0))
            ),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = zone
        )

        val result = ScheduleValidator.validateSchedule(schedule)
        assertFalse(result.isValid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Every N days interval must be greater than 0") })
    }
}
