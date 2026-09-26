package com.doseflow.app.platform

import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.TestClock
import com.doseflow.app.domain.scheduling.ScheduleReconciler
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.receiver.AlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmAndNotificationTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun alarmSchedulerRequestCodes_forSingleOccurrence_mustAllBeDistinct() {
        val occurrenceId = "occ_sched-123_2026-09-24_08:00"

        val doseCode = AlarmScheduler.getDoseRequestCode(occurrenceId)
        val escalationCode = AlarmScheduler.getEscalationRequestCode(occurrenceId)
        val missedCode = AlarmScheduler.getMissedRequestCode(occurrenceId)
        val snoozeCode = AlarmScheduler.getSnoozeRequestCode(occurrenceId)

        val codes = setOf(doseCode, escalationCode, missedCode, snoozeCode)
        assertEquals("All 4 alarm request codes must be mutually distinct to prevent intent overwrites", 4, codes.size)

        assertNotEquals(doseCode, escalationCode)
        assertNotEquals(doseCode, missedCode)
        assertNotEquals(doseCode, snoozeCode)
        assertNotEquals(escalationCode, missedCode)
        assertNotEquals(escalationCode, snoozeCode)
        assertNotEquals(missedCode, snoozeCode)
    }

    @Test
    fun doseStatusTransitions_takenAction_marksTakenWithTimestamps() {
        val now = Instant.now()
        val initialOccurrence = DoseOccurrence(
            id = "occ-test-1",
            scheduleId = "sched-1",
            medicineId = "med-1",
            scheduledTime = now,
            intendedLocalDate = LocalDate.of(2026, 9, 24),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING
        )

        // User taps "Taken"
        val actionTime = now.plusSeconds(120)
        val takenOccurrence = initialOccurrence.copy(
            status = DoseStatus.TAKEN,
            takenTimestamp = actionTime,
            actionTimestamp = actionTime,
            updatedAt = actionTime
        )

        assertEquals(DoseStatus.TAKEN, takenOccurrence.status)
        assertEquals(actionTime, takenOccurrence.takenTimestamp)
        assertEquals(actionTime, takenOccurrence.actionTimestamp)
        assertTrue(takenOccurrence.isActioned)
    }

    @Test
    fun doseStatusTransitions_skipAction_marksSkippedWithTimestamp() {
        val now = Instant.now()
        val initialOccurrence = DoseOccurrence(
            id = "occ-test-skip",
            scheduleId = "sched-1",
            medicineId = "med-1",
            scheduledTime = now,
            intendedLocalDate = LocalDate.of(2026, 9, 24),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING
        )

        val actionTime = now.plusSeconds(300)
        val skippedOccurrence = initialOccurrence.copy(
            status = DoseStatus.SKIPPED,
            actionTimestamp = actionTime,
            takenTimestamp = null,
            updatedAt = actionTime
        )

        assertEquals(DoseStatus.SKIPPED, skippedOccurrence.status)
        assertEquals(actionTime, skippedOccurrence.actionTimestamp)
        assertNull(skippedOccurrence.takenTimestamp)
        assertTrue(skippedOccurrence.isActioned)
    }

    @Test
    fun doseStatusTransitions_snoozeAction_setsSnoozedUntilPlus10MinutesWithoutDuplicateRecord() {
        val now = Instant.now()
        val initialOccurrence = DoseOccurrence(
            id = "occ-test-snooze",
            scheduleId = "sched-1",
            medicineId = "med-1",
            scheduledTime = now,
            intendedLocalDate = LocalDate.of(2026, 9, 24),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING
        )

        val snoozeTime = now.plus(AlarmReceiver.SNOOZE_DURATION)
        val snoozedOccurrence = initialOccurrence.copy(
            status = DoseStatus.SNOOZED,
            snoozedUntil = snoozeTime,
            updatedAt = now
        )

        assertEquals(DoseStatus.SNOOZED, snoozedOccurrence.status)
        assertEquals("Snooze duration must be exactly 10 minutes", 600L, Duration.between(now, snoozedOccurrence.snoozedUntil!!).seconds)
        assertEquals("Must maintain identical occurrenceId (no duplicate record created)", initialOccurrence.id, snoozedOccurrence.id)
        assertFalse("Snoozed occurrence is not terminal actioned", snoozedOccurrence.isActioned)
    }

    @Test
    fun escalationAndMissedRules_escalateAt15m_markMissedAt45m() {
        // Scheduled time: 08:00
        val scheduledTime = LocalDateTime.of(2026, 9, 24, 8, 0).atZone(zone).toInstant()
        val initialOccurrence = DoseOccurrence(
            id = "occ-test-escalation",
            scheduleId = "sched-1",
            medicineId = "med-1",
            scheduledTime = scheduledTime,
            intendedLocalDate = LocalDate.of(2026, 9, 24),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING,
            escalationCount = 0
        )

        // At +15 mins (08:15): escalation triggers
        val escalationTime = scheduledTime.plus(AlarmReceiver.DEFAULT_ESCALATION_DELAY)
        val escalatedOccurrence = initialOccurrence.copy(
            escalationCount = 1,
            updatedAt = escalationTime
        )
        assertEquals(1, escalatedOccurrence.escalationCount)
        assertEquals(DoseStatus.PENDING, escalatedOccurrence.status)

        // At +45 mins total (08:45, which is 30 mins after escalation): auto-missed triggers
        val missedTime = escalationTime.plus(AlarmReceiver.DEFAULT_MISSED_DELAY)
        assertEquals("Total time to missed from scheduled time must be 45 minutes", 45L, Duration.between(scheduledTime, missedTime).toMinutes())

        val missedOccurrence = escalatedOccurrence.copy(
            status = DoseStatus.MISSED,
            actionTimestamp = missedTime,
            updatedAt = missedTime
        )
        assertEquals(DoseStatus.MISSED, missedOccurrence.status)
        assertTrue(missedOccurrence.isActioned)
    }

    @Test
    fun snoozedOccurrence_resetsMissedDeadline_andReconcilesTargetCorrectly() {
        val scheduledTime = LocalDateTime.of(2026, 9, 24, 8, 0).atZone(zone).toInstant()
        val snoozeTime = scheduledTime.plus(Duration.ofMinutes(10)) // Snoozed to 08:10

        val snoozedOccurrence = DoseOccurrence(
            id = "occ-test-snooze-reconcile",
            scheduleId = "sched-1",
            medicineId = "med-1",
            scheduledTime = scheduledTime,
            intendedLocalDate = LocalDate.of(2026, 9, 24),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.SNOOZED,
            snoozedUntil = snoozeTime
        )

        val schedule = Schedule(
            id = "sched-1",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 9, 24),
            timeZoneId = zone
        )

        // Test clock at 08:05 (during the 10m snooze window)
        val testClock = TestClock(
            currentInstant = scheduledTime.plus(Duration.ofMinutes(5)),
            currentZone = zone
        )
        val reconciler = ScheduleReconciler(clock = testClock)

        val result = reconciler.reconcile(
            schedules = listOf(schedule),
            existingOccurrences = listOf(snoozedOccurrence)
        )

        // Next alarm trigger must point to the snooze expiration time (08:10), not next day
        assertEquals("Next alarm target must be snooze time (08:10)", snoozeTime, result.nextAlarmTrigger)
        assertEquals(0, result.autoMarkedMissedCount)
    }

    @Test
    fun appPermissionStatus_flagsExactAlarmAndNotificationDeficits() {
        val deniedStatus = com.doseflow.app.platform.util.AppPermissionStatus(
            canScheduleExactAlarms = false,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = false
        )
        assertTrue("Must flag exact alarm issue when permission is revoked", deniedStatus.hasExactAlarmIssue)
        assertFalse("Notification is enabled so no notification issue", deniedStatus.hasNotificationIssue)
        assertFalse("App is not fully configured without exact alarms", deniedStatus.isFullyConfigured)

        val grantedStatus = com.doseflow.app.platform.util.AppPermissionStatus(
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )
        assertFalse(grantedStatus.hasExactAlarmIssue)
        assertFalse(grantedStatus.hasNotificationIssue)
        assertTrue(grantedStatus.isFullyConfigured)
    }
}
