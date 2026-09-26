package com.doseflow.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ZoneTrackingPolicy {
    FOLLOW_DEVICE_ZONE,
    KEEP_ORIGINAL_ZONE
}

sealed interface RecurrenceRule {
    data class DailyFixedTimes(
        val timesOfDay: List<LocalTime>
    ) : RecurrenceRule

    data class DaysOfWeek(
        val daysOfWeek: Set<DayOfWeek>,
        val timesOfDay: List<LocalTime>
    ) : RecurrenceRule

    data class EveryNDays(
        val intervalDays: Int,
        val anchorDate: LocalDate,
        val timesOfDay: List<LocalTime>
    ) : RecurrenceRule

    data class HourlyInterval(
        val intervalHours: Int,
        val anchorInstant: Instant
    ) : RecurrenceRule

    data class OneOff(
        val targetDateTime: LocalDateTime
    ) : RecurrenceRule

    data object AsNeeded : RecurrenceRule
}

data class Schedule(
    val id: String,
    val medicineId: String,
    val rule: RecurrenceRule,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timeZoneId: ZoneId,
    val zoneTrackingPolicy: ZoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
