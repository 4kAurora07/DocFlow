package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.AppClock
import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.Schedule
import java.time.Duration
import java.time.Instant

data class ReconciliationResult(
    val occurrencesToInsert: List<DoseOccurrence>,
    val occurrencesToUpdate: List<DoseOccurrence>,
    val nextAlarmTrigger: Instant?,
    val immediateAlertOccurrences: List<DoseOccurrence>,
    val autoMarkedMissedCount: Int
)

class ScheduleReconciler(
    private val clock: AppClock,
    private val missedGracePeriod: Duration = Duration.ofMinutes(45),
    private val lookaheadWindow: Duration = Duration.ofDays(2),
    private val lookbackWindow: Duration = Duration.ofDays(1)
) {

    fun reconcile(
        schedules: List<Schedule>,
        existingOccurrences: List<DoseOccurrence>
    ): ReconciliationResult {
        val now = clock.now()
        val zone = clock.zoneId()

        val windowStart = now.minus(lookbackWindow)
        val windowEnd = now.plus(lookaheadWindow)

        val existingById = existingOccurrences.associateBy { it.id }.toMutableMap()

        val toInsert = mutableListOf<DoseOccurrence>()
        val toUpdate = mutableListOf<DoseOccurrence>()
        val immediateAlerts = mutableListOf<DoseOccurrence>()
        var autoMissedCount = 0

        // 1. Calculate desired occurrences across all active schedules
        val activeSchedules = schedules.filter { it.isActive }
        val calculated = mutableListOf<CalculatedOccurrence>()
        for (schedule in activeSchedules) {
            val occs = OccurrenceCalculator.calculateOccurrences(
                schedule = schedule,
                windowStart = windowStart,
                windowEnd = windowEnd,
                deviceZone = zone
            )
            calculated.addAll(occs)
        }

        // 2. Reconcile each calculated occurrence against existing records
        for (calc in calculated) {
            val existing = existingById[calc.occurrenceId]
            val trigger = calc.triggerInstant

            if (existing == null) {
                // New occurrence
                if (trigger.isBefore(now)) {
                    val age = Duration.between(trigger, now)
                    if (age > missedGracePeriod) {
                        // Past downtime threshold: mark missed directly, no notification burst
                        val missedOcc = DoseOccurrence(
                            id = calc.occurrenceId,
                            scheduleId = calc.scheduleId,
                            medicineId = calc.medicineId,
                            scheduledTime = calc.triggerInstant,
                            intendedLocalDate = calc.intendedLocalDate,
                            intendedLocalTime = calc.intendedLocalTime,
                            status = DoseStatus.MISSED,
                            actionTimestamp = now,
                            updatedAt = now
                        )
                        toInsert.add(missedOcc)
                        existingById[missedOcc.id] = missedOcc
                        autoMissedCount++
                    } else {
                        // Within grace window: alert immediately as catch-up
                        val pendingOcc = DoseOccurrence(
                            id = calc.occurrenceId,
                            scheduleId = calc.scheduleId,
                            medicineId = calc.medicineId,
                            scheduledTime = calc.triggerInstant,
                            intendedLocalDate = calc.intendedLocalDate,
                            intendedLocalTime = calc.intendedLocalTime,
                            status = DoseStatus.PENDING,
                            updatedAt = now
                        )
                        toInsert.add(pendingOcc)
                        existingById[pendingOcc.id] = pendingOcc
                        immediateAlerts.add(pendingOcc)
                    }
                } else {
                    // Future occurrence
                    val futureOcc = DoseOccurrence(
                        id = calc.occurrenceId,
                        scheduleId = calc.scheduleId,
                        medicineId = calc.medicineId,
                        scheduledTime = calc.triggerInstant,
                        intendedLocalDate = calc.intendedLocalDate,
                        intendedLocalTime = calc.intendedLocalTime,
                        status = DoseStatus.PENDING,
                        updatedAt = now
                    )
                    toInsert.add(futureOcc)
                    existingById[futureOcc.id] = futureOcc
                }
            } else {
                // Existing record
                if (existing.status == DoseStatus.PENDING) {
                    if (trigger.isBefore(now)) {
                        val age = Duration.between(trigger, now)
                        if (age > missedGracePeriod) {
                            val updated = existing.copy(
                                status = DoseStatus.MISSED,
                                actionTimestamp = now,
                                updatedAt = now
                            )
                            toUpdate.add(updated)
                            existingById[updated.id] = updated
                            autoMissedCount++
                        } else {
                            immediateAlerts.add(existing)
                        }
                    }
                } else if (existing.status == DoseStatus.SNOOZED) {
                    val snoozedUntil = existing.snoozedUntil
                    if (snoozedUntil != null && snoozedUntil.isBefore(now)) {
                        val age = Duration.between(snoozedUntil, now)
                        if (age > missedGracePeriod) {
                            val updated = existing.copy(
                                status = DoseStatus.MISSED,
                                actionTimestamp = now,
                                updatedAt = now
                            )
                            toUpdate.add(updated)
                            existingById[updated.id] = updated
                            autoMissedCount++
                        } else {
                            immediateAlerts.add(existing)
                        }
                    }
                }
                // TAKEN, SKIPPED, MISSED are terminal states and preserved intact
            }
        }

        // 3. Find next alarm trigger
        val allPending = existingById.values.filter {
            it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED
        }

        val nextTrigger = allPending
            .mapNotNull { occ ->
                val targetTime = if (occ.status == DoseStatus.SNOOZED && occ.snoozedUntil != null) {
                    occ.snoozedUntil
                } else {
                    occ.scheduledTime
                }
                if (!targetTime.isBefore(now)) targetTime else null
            }
            .minOrNull()

        return ReconciliationResult(
            occurrencesToInsert = toInsert,
            occurrencesToUpdate = toUpdate,
            nextAlarmTrigger = nextTrigger,
            immediateAlertOccurrences = immediateAlerts,
            autoMarkedMissedCount = autoMissedCount
        )
    }
}
