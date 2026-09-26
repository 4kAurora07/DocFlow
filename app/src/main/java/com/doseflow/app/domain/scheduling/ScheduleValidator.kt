package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ValidationResult

object ScheduleValidator {

    fun validateSchedule(schedule: Schedule): ValidationResult {
        val errors = mutableListOf<String>()

        if (schedule.id.isBlank()) {
            errors.add("Schedule ID must not be blank.")
        }
        if (schedule.medicineId.isBlank()) {
            errors.add("Medicine ID must not be blank.")
        }

        if (schedule.endDate != null && schedule.endDate.isBefore(schedule.startDate)) {
            errors.add("End date (${schedule.endDate}) cannot be before start date (${schedule.startDate}).")
        }

        when (val rule = schedule.rule) {
            is RecurrenceRule.DailyFixedTimes -> {
                if (rule.timesOfDay.isEmpty()) {
                    errors.add("Daily fixed times schedule must include at least one reminder time.")
                }
                if (rule.timesOfDay.toSet().size != rule.timesOfDay.size) {
                    errors.add("Daily fixed times must not contain duplicate reminder times.")
                }
            }
            is RecurrenceRule.DaysOfWeek -> {
                if (rule.daysOfWeek.isEmpty()) {
                    errors.add("Days of week schedule must select at least one day.")
                }
                if (rule.timesOfDay.isEmpty()) {
                    errors.add("Days of week schedule must include at least one reminder time.")
                }
                if (rule.timesOfDay.toSet().size != rule.timesOfDay.size) {
                    errors.add("Reminder times must not contain duplicates.")
                }
            }
            is RecurrenceRule.EveryNDays -> {
                if (rule.intervalDays <= 0) {
                    errors.add("Every N days interval must be greater than 0 (got: ${rule.intervalDays}).")
                }
                if (rule.timesOfDay.isEmpty()) {
                    errors.add("Every N days schedule must include at least one reminder time.")
                }
                if (rule.timesOfDay.toSet().size != rule.timesOfDay.size) {
                    errors.add("Reminder times must not contain duplicates.")
                }
            }
            is RecurrenceRule.HourlyInterval -> {
                if (rule.intervalHours <= 0) {
                    errors.add("Hourly interval must be greater than 0 (got: ${rule.intervalHours}).")
                }
            }
            is RecurrenceRule.OneOff -> {
                // OneOff requires valid targetDateTime
            }
            is RecurrenceRule.AsNeeded -> {
                // AsNeeded is a marker for PRN doses
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
