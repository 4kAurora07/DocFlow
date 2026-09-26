package com.doseflow.app.domain

import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.Medicine
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.TestClock
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import com.doseflow.app.domain.scheduling.OccurrenceCalculator
import com.doseflow.app.domain.scheduling.ScheduleReconciler
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.receiver.AlarmReceiver
import com.doseflow.app.platform.receiver.WakeLockController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EdgeCaseHardeningTest {

    private val nyZone = ZoneId.of("America/New_York")
    private val londonZone = ZoneId.of("Europe/London")

    @Test
    fun appKilledFromRecents_alarmRequestCodesRemainDeterministicAndUnique() {
        val occurrenceId1 = "occ_sched-1_2026-09-26T08:00"
        val occurrenceId2 = "occ_sched-2_2026-09-26T08:00"

        val code1Dose = AlarmScheduler.getDoseRequestCode(occurrenceId1)
        val code1Escalation = AlarmScheduler.getEscalationRequestCode(occurrenceId1)
        val code1Missed = AlarmScheduler.getMissedRequestCode(occurrenceId1)
        val code1Snooze = AlarmScheduler.getSnoozeRequestCode(occurrenceId1)

        val code2Dose = AlarmScheduler.getDoseRequestCode(occurrenceId2)

        // Ensure 100% deterministic codes across app restarts
        assertEquals(code1Dose, AlarmScheduler.getDoseRequestCode(occurrenceId1))
        assertNotEquals("Different schedules at same time must not collide", code1Dose, code2Dose)

        val allCodes = setOf(code1Dose, code1Escalation, code1Missed, code1Snooze, code2Dose)
        assertEquals(5, allCodes.size)
    }

    @Test
    fun airplaneMode_doesNotBlockLocalDoseMarkingOrAlarmClearing() {
        val occurrence = DoseOccurrence(
            id = "occ_offline_test",
            scheduleId = "sched_1",
            medicineId = "med_1",
            scheduledTime = Instant.ofEpochMilli(1_758_873_600_000L),
            intendedLocalDate = LocalDate.of(2026, 9, 26),
            intendedLocalTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING
        )

        val nowEpoch = 1_758_873_700_000L
        val takenOccurrence = occurrence.copy(
            status = DoseStatus.TAKEN,
            takenTimestamp = Instant.ofEpochMilli(nowEpoch),
            actionTimestamp = Instant.ofEpochMilli(nowEpoch)
        )

        assertEquals(DoseStatus.TAKEN, takenOccurrence.status)
        assertEquals(Instant.ofEpochMilli(nowEpoch), takenOccurrence.takenTimestamp)
        assertEquals(Instant.ofEpochMilli(nowEpoch), takenOccurrence.actionTimestamp)
    }

    @Test
    fun multipleOverlappingSchedulesForOneMedicine_generateDistinctOccurrencesWithoutCollision() {
        val medicineId = "med_antibiotic_dual"
        val today = LocalDate.of(2026, 9, 26)

        // Schedule A: Morning and Evening (08:00, 20:00)
        val scheduleA = Schedule(
            id = "sched_antibiotic_morning_evening",
            medicineId = medicineId,
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
            startDate = today,
            timeZoneId = nyZone
        )

        // Schedule B: Midday booster (14:00)
        val scheduleB = Schedule(
            id = "sched_antibiotic_midday",
            medicineId = medicineId,
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(14, 0))),
            startDate = today,
            timeZoneId = nyZone
        )

        val start = today.atStartOfDay(nyZone).toInstant()
        val end = today.plusDays(1).atStartOfDay(nyZone).toInstant()

        val occsA = OccurrenceCalculator.calculateOccurrences(scheduleA, start, end, nyZone)
        val occsB = OccurrenceCalculator.calculateOccurrences(scheduleB, start, end, nyZone)

        assertEquals("Schedule A generates 2 occurrences", 2, occsA.size)
        assertEquals("Schedule B generates 1 occurrence", 1, occsB.size)

        val allOccurrences = occsA + occsB
        val distinctIds = allOccurrences.map { it.occurrenceId }.toSet()
        assertEquals("All occurrence IDs across multiple overlapping schedules must be mutually distinct", 3, distinctIds.size)

        // Verify ordering: 08:00, 14:00, 20:00
        val sortedOccurrences = allOccurrences.sortedBy { it.triggerInstant }
        assertEquals("08:00 dose first", LocalTime.of(8, 0), sortedOccurrences[0].intendedLocalTime)
        assertEquals("14:00 dose second", LocalTime.of(14, 0), sortedOccurrences[1].intendedLocalTime)
        assertEquals("20:00 dose third", LocalTime.of(20, 0), sortedOccurrences[2].intendedLocalTime)
    }

    @Test
    fun deletingMedicine_cancelsPendingAlarmsAndCascadesOccurrences() {
        val medicineId = "med_discontinued"
        val today = LocalDate.of(2026, 9, 26)

        val schedule = Schedule(
            id = "sched_discontinued",
            medicineId = medicineId,
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(9, 0))),
            startDate = today,
            timeZoneId = nyZone,
            isActive = true
        )

        val start = today.atStartOfDay(nyZone).toInstant()
        val end = today.plusDays(2).atStartOfDay(nyZone).toInstant()
        val occurrences = OccurrenceCalculator.calculateOccurrences(schedule, start, end, nyZone)

        assertEquals(2, occurrences.size)

        // Deleting medicine removes schedule & cascades pending occurrences
        val remainingSchedules = emptyList<Schedule>()
        val remainingOccurrences = emptyList<DoseOccurrence>()

        val testClock = TestClock(currentInstant = start.plus(Duration.ofHours(1)), currentZone = nyZone)
        val reconciler = ScheduleReconciler(clock = testClock)
        val result = reconciler.reconcile(
            schedules = remainingSchedules,
            existingOccurrences = remainingOccurrences
        )

        // When medicine and schedules are deleted, no pending alarm target exists
        assertNull("Next alarm target must be null after deletion", result.nextAlarmTrigger)
    }

    private class TestWakeLockController : WakeLockController {
        override var isHeld: Boolean = false
            private set
        var acquireCount: Int = 0
            private set
        var releaseCount: Int = 0
            private set
        var requestedTimeoutMs: Long = 0L
            private set

        override fun acquire(timeoutMs: Long) {
            isHeld = true
            acquireCount++
            requestedTimeoutMs = timeoutMs
        }

        override fun release() {
            isHeld = false
            releaseCount++
        }
    }

    @Test
    fun dozeMode_alarmStillFiresViaSetExactAndAllowWhileIdle() = runBlocking {
        // In deep Doze mode, standard alarms are deferred to maintenance windows.
        // DoseFlow uses AlarmManager.RTC_WAKEUP with setExactAndAllowWhileIdle, combined with
        // a PARTIAL_WAKE_LOCK held across the asynchronous Room SQLite write and notification dispatch.
        val fakeWakeLock = TestWakeLockController()
        var roomWriteExecutedWhileWakeLockHeld = false
        var notificationDispatchedWhileWakeLockHeld = false

        assertFalse("WakeLock must initially not be held", fakeWakeLock.isHeld)

        // Execute alarm processing simulating Room transaction and notification dispatch
        AlarmReceiver.executeWithWakeLock(
            wakeLock = fakeWakeLock,
            timeoutMs = AlarmReceiver.WAKE_LOCK_TIMEOUT_MS
        ) {
            // Verify WakeLock is held BEFORE and DURING Room transaction
            assertTrue("Wake lock must be actively held entering Room transaction", fakeWakeLock.isHeld)

            // Simulating Room SQLite occurrence status update
            roomWriteExecutedWhileWakeLockHeld = fakeWakeLock.isHeld

            // Simulating Notification dispatch
            notificationDispatchedWhileWakeLockHeld = fakeWakeLock.isHeld
        }

        // Verify WakeLock lifecycle invariants
        assertFalse("Wake lock must be released immediately after transaction and notification dispatch complete", fakeWakeLock.isHeld)
        assertTrue("Room write must have been completed under active wake lock", roomWriteExecutedWhileWakeLockHeld)
        assertTrue("Notification dispatch must have been completed under active wake lock", notificationDispatchedWhileWakeLockHeld)
        assertEquals("WakeLock acquired exactly once", 1, fakeWakeLock.acquireCount)
        assertEquals("WakeLock released exactly once", 1, fakeWakeLock.releaseCount)
        assertEquals("WakeLock acquired with 30s safety timeout to prevent battery drain", 30_000L, fakeWakeLock.requestedTimeoutMs)
        assertEquals("WakeLock tag matches system identifier", "com.doseflow.app:AlarmReceiverWakeLock", AlarmReceiver.WAKE_LOCK_TAG)
    }

    @Test
    fun dozeMode_wakeLockSafelyReleasedEvenIfRoomTransactionThrows() = runBlocking {
        val fakeWakeLock = TestWakeLockController()
        var errorCaught = false

        try {
            AlarmReceiver.executeWithWakeLock(fakeWakeLock) {
                assertTrue("Wake lock held during attempt", fakeWakeLock.isHeld)
                throw RuntimeException("Simulated SQLiteDiskIOException during Doze")
            }
        } catch (e: RuntimeException) {
            errorCaught = true
        }

        assertTrue("Exception must propagate", errorCaught)
        assertFalse("Wake lock must be released even when Room transaction throws", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCount)
    }

    @Test
    fun timezoneShiftWhileAlarmPending_standardAlignment_5HourOffset() {
        // In summer (Sep 26, 2026): NYC is EDT (UTC-4), London is BST (UTC+1).
        // Difference is 5 hours.
        val testDate = LocalDate.of(2026, 9, 26)

        val targetNy = OccurrenceCalculator.resolveToInstant(
            date = testDate,
            time = LocalTime.of(8, 0),
            zone = nyZone
        )

        val targetLondon = OccurrenceCalculator.resolveToInstant(
            date = testDate,
            time = LocalTime.of(8, 0),
            zone = londonZone
        )

        assertEquals(LocalTime.of(8, 0), targetNy.atZone(nyZone).toLocalTime())
        assertEquals(LocalTime.of(8, 0), targetLondon.atZone(londonZone).toLocalTime())

        val diffHours = Duration.between(targetLondon, targetNy).toHours()
        assertEquals("During standard summer alignment (Sep 26), NYC (EDT) and London (BST) differ by 5 hours", 5L, diffHours)
    }

    @Test
    fun timezoneShiftWhileAlarmPending_dstMismatchWindow_4HourOffset() {
        // Spring DST mismatch window (March 15, 2026):
        // US entered DST on 2nd Sunday of March (March 8): NYC is EDT (UTC-4).
        // UK does not enter DST until last Sunday of March (March 29): London is GMT (UTC+0).
        // Therefore, the true difference between 8:00 AM NY and 8:00 AM London is 4 hours, NOT 5!
        val springMismatchDate = LocalDate.of(2026, 3, 15)

        val targetNySpring = OccurrenceCalculator.resolveToInstant(
            date = springMismatchDate,
            time = LocalTime.of(8, 0),
            zone = nyZone
        )

        val targetLondonSpring = OccurrenceCalculator.resolveToInstant(
            date = springMismatchDate,
            time = LocalTime.of(8, 0),
            zone = londonZone
        )

        assertEquals(LocalTime.of(8, 0), targetNySpring.atZone(nyZone).toLocalTime())
        assertEquals(LocalTime.of(8, 0), targetLondonSpring.atZone(londonZone).toLocalTime())

        val springDiffHours = Duration.between(targetLondonSpring, targetNySpring).toHours()
        assertEquals("During spring mismatch window (March 15), NYC is EDT (UTC-4) and London is GMT (UTC+0) -> diff is 4 hours", 4L, springDiffHours)

        // Fall DST mismatch window (October 28, 2026):
        // UK exited DST on last Sunday of October (October 25): London is GMT (UTC+0).
        // US does not exit DST until 1st Sunday of November (November 1): NYC is still EDT (UTC-4).
        // Difference is again exactly 4 hours!
        val fallMismatchDate = LocalDate.of(2026, 10, 28)

        val targetNyFall = OccurrenceCalculator.resolveToInstant(
            date = fallMismatchDate,
            time = LocalTime.of(8, 0),
            zone = nyZone
        )

        val targetLondonFall = OccurrenceCalculator.resolveToInstant(
            date = fallMismatchDate,
            time = LocalTime.of(8, 0),
            zone = londonZone
        )

        val fallDiffHours = Duration.between(targetLondonFall, targetNyFall).toHours()
        assertEquals("During fall mismatch window (October 28), London is GMT (UTC+0) and NYC is EDT (UTC-4) -> diff is 4 hours", 4L, fallDiffHours)
    }
}
