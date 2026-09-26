package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleReconcilerTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun simulatedDowntime_pastGracePeriod_automaticallyMarksMissedWithoutAlarmBurst() {
        val schedule = Schedule(
            id = "sched-downtime",
            medicineId = "med-heart",
            rule = RecurrenceRule.DailyFixedTimes(
                listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
            ),
            startDate = LocalDate.of(2026, 7, 1),
            timeZoneId = zone
        )

        // Device went down at 07:00 on July 1 and boots back up at 20:30 on July 1 (13.5 hours downtime)
        val bootTime = LocalDateTime.of(2026, 7, 1, 20, 30).atZone(zone).toInstant()
        val testClock = TestClock(currentInstant = bootTime, currentZone = zone)

        // Grace period 45 mins:
        // 08:00 dose is 12.5h old -> MISSED
        // 14:00 dose is 6.5h old -> MISSED
        // 20:00 dose is 30 mins old (<= 45 mins) -> Immediate catch-up alert!
        val reconciler = ScheduleReconciler(
            clock = testClock,
            missedGracePeriod = Duration.ofMinutes(45)
        )

        val result = reconciler.reconcile(
            schedules = listOf(schedule),
            existingOccurrences = emptyList()
        )

        // Verify missed occurrences count
        assertEquals("08:00 and 14:00 must be marked missed", 2, result.autoMarkedMissedCount)

        val missed = result.occurrencesToInsert.filter { it.status == DoseStatus.MISSED }
        assertEquals(2, missed.size)
        assertTrue(missed.any { it.intendedLocalTime == LocalTime.of(8, 0) })
        assertTrue(missed.any { it.intendedLocalTime == LocalTime.of(14, 0) })

        // Verify catch-up alert
        assertEquals("Only 20:00 dose within 45-min grace alerts as catch-up", 1, result.immediateAlertOccurrences.size)
        assertEquals(LocalTime.of(20, 0), result.immediateAlertOccurrences.first().intendedLocalTime)

        // Next alarm trigger should be tomorrow's 08:00 dose
        val expectedNextTrigger = LocalDateTime.of(2026, 7, 2, 8, 0).atZone(zone).toInstant()
        assertEquals(expectedNextTrigger, result.nextAlarmTrigger)
    }

    @Test
    fun idempotency_runningReconcileRepeatedly_producesZeroDuplicatesAndZeroChurn() {
        val schedule = Schedule(
            id = "sched-idempotent",
            medicineId = "med-daily",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.of(2026, 8, 1),
            timeZoneId = zone
        )

        val now = LocalDateTime.of(2026, 8, 1, 7, 0).atZone(zone).toInstant() // 2 hours before 9:00 dose
        val testClock = TestClock(currentInstant = now, currentZone = zone)
        val reconciler = ScheduleReconciler(clock = testClock)

        // Pass 1: Empty database
        val pass1Result = reconciler.reconcile(
            schedules = listOf(schedule),
            existingOccurrences = emptyList()
        )

        val initialInserted = pass1Result.occurrencesToInsert
        assertTrue(initialInserted.isNotEmpty())

        // Pass 2: Re-run with the inserted occurrences now existing in database
        val pass2Result = reconciler.reconcile(
            schedules = listOf(schedule),
            existingOccurrences = initialInserted
        )

        assertEquals("No new occurrences should be inserted on second pass", 0, pass2Result.occurrencesToInsert.size)
        assertEquals("No occurrences should be updated on second pass", 0, pass2Result.occurrencesToUpdate.size)
        assertEquals("Next alarm trigger must match exactly", pass1Result.nextAlarmTrigger, pass2Result.nextAlarmTrigger)
    }

    @Test
    fun userActionPreservation_takenOrSkippedDoses_areNeverReverted() {
        val schedule = Schedule(
            id = "sched-action-preserve",
            medicineId = "med-vitamins",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 9, 1),
            timeZoneId = zone
        )

        val doseTime = LocalDateTime.of(2026, 9, 1, 8, 0).atZone(zone).toInstant()
        val takenTime = LocalDateTime.of(2026, 9, 1, 8, 5).atZone(zone).toInstant()

        // Existing occurrence in Room, marked TAKEN by user
        val existingTakenOcc = DoseOccurrence(
            id = "occ_sched-action-preserve_2026-09-01_08:00",
            scheduleId = schedule.id,
            medicineId = schedule.medicineId,
            scheduledTime = doseTime,
            intendedLocalDate = LocalDate.of(2026, 9, 1),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.TAKEN,
            actionTimestamp = takenTime,
            takenTimestamp = takenTime
        )

        // Current time is 11:00 (3 hours later)
        val clockNow = LocalDateTime.of(2026, 9, 1, 11, 0).atZone(zone).toInstant()
        val testClock = TestClock(currentInstant = clockNow, currentZone = zone)
        val reconciler = ScheduleReconciler(clock = testClock)

        val result = reconciler.reconcile(
            schedules = listOf(schedule),
            existingOccurrences = listOf(existingTakenOcc)
        )

        // Ensure the taken dose was NOT updated to MISSED or PENDING
        assertTrue(result.occurrencesToUpdate.none { it.id == existingTakenOcc.id })
        assertEquals(0, result.autoMarkedMissedCount)
    }

    @Test
    fun clockChange_forwardJump_reconcilesGracefully() {
        val schedule = Schedule(
            id = "sched-clock-jump",
            medicineId = "med-jump",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(10, 0), LocalTime.of(18, 0))),
            startDate = LocalDate.of(2026, 10, 1),
            timeZoneId = zone
        )

        // Clock starts at 09:00
        val t0 = LocalDateTime.of(2026, 10, 1, 9, 0).atZone(zone).toInstant()
        val testClock = TestClock(currentInstant = t0, currentZone = zone)
        val reconciler = ScheduleReconciler(clock = testClock)

        val pass1 = reconciler.reconcile(listOf(schedule), emptyList())
        val occurrences = pass1.occurrencesToInsert

        // User or network abruptly jumps clock forward by 12 hours to 21:00
        val t1 = LocalDateTime.of(2026, 10, 1, 21, 0).atZone(zone).toInstant()
        testClock.setInstant(t1)

        val pass2 = reconciler.reconcile(listOf(schedule), occurrences)

        // Both 10:00 and 18:00 are now past the 45-min grace window -> both marked MISSED
        val updatedMissed = pass2.occurrencesToUpdate.filter { it.status == DoseStatus.MISSED }
        assertEquals(2, updatedMissed.size)

        // Next alarm trigger points to tomorrow's 10:00 dose
        assertNotNull(pass2.nextAlarmTrigger)
        val expectedNext = LocalDateTime.of(2026, 10, 2, 10, 0).atZone(zone).toInstant()
        assertEquals(expectedNext, pass2.nextAlarmTrigger)
    }
}
