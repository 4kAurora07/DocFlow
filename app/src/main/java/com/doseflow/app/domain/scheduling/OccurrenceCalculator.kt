package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CalculatedOccurrence(
    val occurrenceId: String,
    val scheduleId: String,
    val medicineId: String,
    val triggerInstant: Instant,
    val intendedLocalDate: LocalDate,
    val intendedLocalTime: LocalTime
)

object OccurrenceCalculator {

    /**
     * Resolves a local date and time in the specified ZoneId to an absolute Instant,
     * applying deterministic DST handling:
     * - Gap (spring forward, e.g. 02:30 does not exist): shifts forward to the transition boundary instant.
     * - Overlap (fall back, e.g. 01:30 occurs twice): uses the earlier offset (first transition)
     *   to alert at the earlier instant and prevent duplicate alarms.
     */
    fun resolveToInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val ldt = LocalDateTime.of(date, time)
        val rules = zone.rules
        val validOffsets = rules.getValidOffsets(ldt)
        return if (validOffsets.isNotEmpty()) {
            // Normal case (1 offset) or Overlap (2 offsets).
            // For overlap, validOffsets.first() is the earlier offset (earlier UTC instant).
            ldt.atOffset(validOffsets.first()).toInstant()
        } else {
            // Gap (spring forward): local time does not exist.
            // Shift to the instant when the gap ends (the transition instant).
            val transition = rules.getTransition(ldt)
                ?: error("Expected transition for non-valid local time: $ldt in zone $zone")
            transition.instant
        }
    }

    /**
     * Computes all scheduled occurrences within [windowStart, windowEnd] (inclusive).
     */
    fun calculateOccurrences(
        schedule: Schedule,
        windowStart: Instant,
        windowEnd: Instant,
        deviceZone: ZoneId
    ): List<CalculatedOccurrence> {
        if (!schedule.isActive) return emptyList()

        val effectiveZone = when (schedule.zoneTrackingPolicy) {
            ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE -> deviceZone
            ZoneTrackingPolicy.KEEP_ORIGINAL_ZONE -> schedule.timeZoneId
        }

        return when (val rule = schedule.rule) {
            is RecurrenceRule.DailyFixedTimes -> calculateDailyFixed(
                schedule, rule, windowStart, windowEnd, effectiveZone
            )
            is RecurrenceRule.DaysOfWeek -> calculateDaysOfWeek(
                schedule, rule, windowStart, windowEnd, effectiveZone
            )
            is RecurrenceRule.EveryNDays -> calculateEveryNDays(
                schedule, rule, windowStart, windowEnd, effectiveZone
            )
            is RecurrenceRule.HourlyInterval -> calculateHourlyInterval(
                schedule, rule, windowStart, windowEnd, effectiveZone
            )
            is RecurrenceRule.OneOff -> calculateOneOff(
                schedule, rule, windowStart, windowEnd, effectiveZone
            )
            is RecurrenceRule.AsNeeded -> emptyList()
        }
    }

    private fun calculateDailyFixed(
        schedule: Schedule,
        rule: RecurrenceRule.DailyFixedTimes,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): List<CalculatedOccurrence> {
        val startDate = schedule.startDate
        val endDate = schedule.endDate

        // Compute local date window in the effective zone
        val localStart = windowStart.atZone(zone).toLocalDate().minusDays(1)
        val localEnd = windowEnd.atZone(zone).toLocalDate().plusDays(1)

        val evalStart = if (localStart.isBefore(startDate)) startDate else localStart
        val evalEnd = if (endDate != null && localEnd.isAfter(endDate)) endDate else localEnd

        if (evalStart.isAfter(evalEnd)) return emptyList()

        val result = mutableListOf<CalculatedOccurrence>()
        var currDate = evalStart
        while (!currDate.isAfter(evalEnd)) {
            for (time in rule.timesOfDay) {
                val trigger = resolveToInstant(currDate, time, zone)
                if (!trigger.isBefore(windowStart) && !trigger.isAfter(windowEnd)) {
                    val id = generateOccurrenceId(schedule.id, currDate, time)
                    result.add(
                        CalculatedOccurrence(
                            occurrenceId = id,
                            scheduleId = schedule.id,
                            medicineId = schedule.medicineId,
                            triggerInstant = trigger,
                            intendedLocalDate = currDate,
                            intendedLocalTime = time
                        )
                    )
                }
            }
            currDate = currDate.plusDays(1)
        }
        return result.sortedBy { it.triggerInstant }
    }

    private fun calculateDaysOfWeek(
        schedule: Schedule,
        rule: RecurrenceRule.DaysOfWeek,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): List<CalculatedOccurrence> {
        val startDate = schedule.startDate
        val endDate = schedule.endDate

        val localStart = windowStart.atZone(zone).toLocalDate().minusDays(1)
        val localEnd = windowEnd.atZone(zone).toLocalDate().plusDays(1)

        val evalStart = if (localStart.isBefore(startDate)) startDate else localStart
        val evalEnd = if (endDate != null && localEnd.isAfter(endDate)) endDate else localEnd

        if (evalStart.isAfter(evalEnd)) return emptyList()

        val result = mutableListOf<CalculatedOccurrence>()
        var currDate = evalStart
        while (!currDate.isAfter(evalEnd)) {
            if (currDate.dayOfWeek in rule.daysOfWeek) {
                for (time in rule.timesOfDay) {
                    val trigger = resolveToInstant(currDate, time, zone)
                    if (!trigger.isBefore(windowStart) && !trigger.isAfter(windowEnd)) {
                        val id = generateOccurrenceId(schedule.id, currDate, time)
                        result.add(
                            CalculatedOccurrence(
                                occurrenceId = id,
                                scheduleId = schedule.id,
                                medicineId = schedule.medicineId,
                                triggerInstant = trigger,
                                intendedLocalDate = currDate,
                                intendedLocalTime = time
                            )
                        )
                    }
                }
            }
            currDate = currDate.plusDays(1)
        }
        return result.sortedBy { it.triggerInstant }
    }

    private fun calculateEveryNDays(
        schedule: Schedule,
        rule: RecurrenceRule.EveryNDays,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): List<CalculatedOccurrence> {
        val startDate = schedule.startDate
        val endDate = schedule.endDate
        val anchorDate = rule.anchorDate

        val localStart = windowStart.atZone(zone).toLocalDate().minusDays(1)
        val localEnd = windowEnd.atZone(zone).toLocalDate().plusDays(1)

        val evalStart = if (localStart.isBefore(startDate)) startDate else localStart
        val evalEnd = if (endDate != null && localEnd.isAfter(endDate)) endDate else localEnd

        if (evalStart.isAfter(evalEnd)) return emptyList()

        val result = mutableListOf<CalculatedOccurrence>()
        var currDate = evalStart
        while (!currDate.isAfter(evalEnd)) {
            val daysBetween = ChronoUnit.DAYS.between(anchorDate, currDate)
            if (daysBetween >= 0 && daysBetween % rule.intervalDays == 0L) {
                for (time in rule.timesOfDay) {
                    val trigger = resolveToInstant(currDate, time, zone)
                    if (!trigger.isBefore(windowStart) && !trigger.isAfter(windowEnd)) {
                        val id = generateOccurrenceId(schedule.id, currDate, time)
                        result.add(
                            CalculatedOccurrence(
                                occurrenceId = id,
                                scheduleId = schedule.id,
                                medicineId = schedule.medicineId,
                                triggerInstant = trigger,
                                intendedLocalDate = currDate,
                                intendedLocalTime = time
                            )
                        )
                    }
                }
            }
            currDate = currDate.plusDays(1)
        }
        return result.sortedBy { it.triggerInstant }
    }

    /**
     * Interval schedules: computes exact elapsed hours anchored at anchorInstant.
     * Zero-drift formula: T_k = anchorInstant + k * (intervalHours * 3600s).
     */
    private fun calculateHourlyInterval(
        schedule: Schedule,
        rule: RecurrenceRule.HourlyInterval,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): List<CalculatedOccurrence> {
        val anchor = rule.anchorInstant
        val intervalSeconds = rule.intervalHours.toLong() * 3600L

        // Occurrence index calculation
        val effectiveStart = if (windowStart.isBefore(anchor)) anchor else windowStart
        if (effectiveStart.isAfter(windowEnd)) return emptyList()

        val startDiffSeconds = Duration.between(anchor, effectiveStart).seconds
        val kMin = if (startDiffSeconds <= 0L) 0L else (startDiffSeconds / intervalSeconds)

        val endDiffSeconds = Duration.between(anchor, windowEnd).seconds
        if (endDiffSeconds < 0L) return emptyList()
        val kMax = endDiffSeconds / intervalSeconds

        val result = mutableListOf<CalculatedOccurrence>()
        for (k in kMin..kMax) {
            val trigger = anchor.plus(Duration.ofSeconds(k * intervalSeconds))
            if (trigger.isBefore(windowStart) || trigger.isAfter(windowEnd)) continue

            val zdt = trigger.atZone(zone)
            val occDate = zdt.toLocalDate()
            val occTime = zdt.toLocalTime()

            // Check course boundaries
            if (occDate.isBefore(schedule.startDate)) continue
            if (schedule.endDate != null && occDate.isAfter(schedule.endDate)) continue

            val id = "occ_${schedule.id}_idx_${k}"
            result.add(
                CalculatedOccurrence(
                    occurrenceId = id,
                    scheduleId = schedule.id,
                    medicineId = schedule.medicineId,
                    triggerInstant = trigger,
                    intendedLocalDate = occDate,
                    intendedLocalTime = occTime
                )
            )
        }
        return result.sortedBy { it.triggerInstant }
    }

    private fun calculateOneOff(
        schedule: Schedule,
        rule: RecurrenceRule.OneOff,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): List<CalculatedOccurrence> {
        val targetDate = rule.targetDateTime.toLocalDate()
        val targetTime = rule.targetDateTime.toLocalTime()

        if (targetDate.isBefore(schedule.startDate)) return emptyList()
        if (schedule.endDate != null && targetDate.isAfter(schedule.endDate)) return emptyList()

        val trigger = resolveToInstant(targetDate, targetTime, zone)
        if (trigger.isBefore(windowStart) || trigger.isAfter(windowEnd)) return emptyList()

        val id = generateOccurrenceId(schedule.id, targetDate, targetTime)
        return listOf(
            CalculatedOccurrence(
                occurrenceId = id,
                scheduleId = schedule.id,
                medicineId = schedule.medicineId,
                triggerInstant = trigger,
                intendedLocalDate = targetDate,
                intendedLocalTime = targetTime
            )
        )
    }

    fun generateOccurrenceId(scheduleId: String, date: LocalDate, time: LocalTime): String {
        return "occ_${scheduleId}_${date}_${time}"
    }
}
