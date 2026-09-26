package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.AppClock
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.TestClock
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DstAndZoneTest {

    private val nyZone = ZoneId.of("America/New_York")
    private val londonZone = ZoneId.of("Europe/London")

    @Test
    fun dstSpringForward_gapTime_shiftsToTransitionBoundary() {
        // In America/New_York on March 8, 2026:
        // At 02:00:00, clocks spring forward to 03:00:00.
        // 02:30:00 is a non-existent wall-clock gap.
        val schedule = Schedule(
            id = "sched-spring-gap",
            medicineId = "med-gap",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(2, 30))),
            startDate = LocalDate.of(2026, 3, 8),
            timeZoneId = nyZone,
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE
        )

        val windowStart = LocalDateTime.of(2026, 3, 8, 0, 0).atZone(nyZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 3, 8, 23, 59).atZone(nyZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, nyZone
        )

        assertEquals("Should produce exactly 1 occurrence on gap day", 1, occurrences.size)
        val occ = occurrences.first()

        // The intended local time was 02:30
        assertEquals(LocalTime.of(2, 30), occ.intendedLocalTime)
        assertEquals(LocalDate.of(2026, 3, 8), occ.intendedLocalDate)

        // The absolute trigger instant shifts to 03:00:00 EDT (the transition boundary)
        val localTrigger = occ.triggerInstant.atZone(nyZone).toLocalTime()
        assertEquals("Trigger must land on the 03:00:00 transition boundary", LocalTime.of(3, 0), localTrigger)
    }

    @Test
    fun dstFallBack_overlapTime_triggersOnFirstTransitionWithoutDuplicates() {
        // In America/New_York on November 1, 2026:
        // Clocks fall back from 02:00 EDT to 01:00 EST.
        // The hour from 01:00 to 02:00 repeats twice.
        val schedule = Schedule(
            id = "sched-fall-overlap",
            medicineId = "med-overlap",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(1, 30))),
            startDate = LocalDate.of(2026, 11, 1),
            timeZoneId = nyZone,
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE
        )

        val windowStart = LocalDateTime.of(2026, 11, 1, 0, 0).atZone(nyZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 11, 1, 23, 59).atZone(nyZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, nyZone
        )

        // Must trigger exactly once (on the first overlap hour, earlier UTC instant), no duplicate alarms
        assertEquals("Must produce exactly 1 occurrence during overlap day", 1, occurrences.size)
        val occ = occurrences.first()

        assertEquals(LocalTime.of(1, 30), occ.intendedLocalTime)
        // Check that the offset used was the first (EDT = UTC-4, before the fall-back to EST = UTC-5)
        val offset = occ.triggerInstant.atZone(nyZone).offset
        assertEquals(java.time.ZoneOffset.ofHours(-4), offset)
    }

    @Test
    fun zoneTrackingPolicy_followDeviceZone_adjustsToLocalDeviceTime() {
        // User takes medication at 08:00 AM every day
        val schedule = Schedule(
            id = "sched-travel-follow",
            medicineId = "med-travel",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 6, 1),
            timeZoneId = nyZone,
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE
        )

        val date = LocalDate.of(2026, 6, 15)
        val windowStartLondon = date.atStartOfDay(londonZone).toInstant()
        val windowEndLondon = date.plusDays(1).atStartOfDay(londonZone).toInstant()

        // When device is in London
        val occsInLondon = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStartLondon, windowEndLondon, londonZone
        )

        assertEquals(1, occsInLondon.size)
        val triggerInLondon = occsInLondon.first().triggerInstant.atZone(londonZone)
        assertEquals("Follows device zone: triggers at 08:00 in London", LocalTime.of(8, 0), triggerInLondon.toLocalTime())
    }

    @Test
    fun zoneTrackingPolicy_keepOriginalZone_anchorsToOriginalTimeZone() {
        // Strict immunosuppressant/therapy requiring fixed UTC elapsed timing
        val schedule = Schedule(
            id = "sched-travel-keep",
            medicineId = "med-strict",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 6, 1),
            timeZoneId = nyZone, // Created in New York (EDT = UTC-4)
            zoneTrackingPolicy = ZoneTrackingPolicy.KEEP_ORIGINAL_ZONE
        )

        val date = LocalDate.of(2026, 6, 15)
        val windowStart = date.atStartOfDay(londonZone).toInstant()
        val windowEnd = date.plusDays(1).atStartOfDay(londonZone).toInstant()

        // When device is in London (BST = UTC+1, 5 hours ahead of NY)
        val occs = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, londonZone
        )

        assertEquals(1, occs.size)
        val triggerNy = occs.first().triggerInstant.atZone(nyZone)
        val triggerLondon = occs.first().triggerInstant.atZone(londonZone)

        assertEquals("Anchored to NY: triggers at 08:00 NY time", LocalTime.of(8, 0), triggerNy.toLocalTime())
        assertEquals("In London, 08:00 EDT corresponds to 13:00 BST", LocalTime.of(13, 0), triggerLondon.toLocalTime())
    }

    /**
     * User Requirement 2:
     * Explicit DST-boundary collision test.
     * Two independent schedules:
     * - Schedule A: 02:30 AM dose under FOLLOW_DEVICE_ZONE that shifts to 03:00 AM spring-forward boundary.
     * - Schedule B: separate schedule already fixed at 03:00 AM.
     * Both land on the same wall-clock time (03:00 AM) on the transition day.
     * Verify:
     * 1. Both occurrences get distinct deterministic occurrenceIds.
     * 2. Both are correctly reconciled.
     * 3. Neither is dropped or silently merged.
     */
    @Test
    fun dstBoundaryCollision_twoSchedulesCollideAtTransitionBoundary_bothPreservedWithDistinctIds() {
        val transitionDate = LocalDate.of(2026, 3, 8) // Spring forward day in America/New_York

        // Schedule A: 02:30 AM (non-existent gap, shifts forward to 03:00 AM)
        val scheduleA = Schedule(
            id = "sched-A-gap-230am",
            medicineId = "med-aspirin",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(2, 30))),
            startDate = transitionDate,
            endDate = transitionDate,
            timeZoneId = nyZone,
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE
        )

        // Schedule B: fixed at 03:00 AM (valid wall-clock time right after gap)
        val scheduleB = Schedule(
            id = "sched-B-fixed-300am",
            medicineId = "med-blood-pressure",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(3, 0))),
            startDate = transitionDate,
            endDate = transitionDate,
            timeZoneId = nyZone,
            zoneTrackingPolicy = ZoneTrackingPolicy.FOLLOW_DEVICE_ZONE
        )

        val windowStart = transitionDate.atStartOfDay(nyZone).toInstant()
        val windowEnd = transitionDate.plusDays(1).atStartOfDay(nyZone).toInstant()

        // 1. Calculate occurrences for each schedule
        val occsA = OccurrenceCalculator.calculateOccurrences(scheduleA, windowStart, windowEnd, nyZone)
        val occsB = OccurrenceCalculator.calculateOccurrences(scheduleB, windowStart, windowEnd, nyZone)

        assertEquals("Schedule A must produce 1 occurrence", 1, occsA.size)
        assertEquals("Schedule B must produce 1 occurrence", 1, occsB.size)

        val occA = occsA.first()
        val occB = occsB.first()

        // Both trigger at the EXACT same instant (03:00:00 EDT)
        assertEquals("Both occurrences must trigger at the exact same instant", occA.triggerInstant, occB.triggerInstant)
        assertEquals(LocalTime.of(3, 0), occA.triggerInstant.atZone(nyZone).toLocalTime())
        assertEquals(LocalTime.of(3, 0), occB.triggerInstant.atZone(nyZone).toLocalTime())

        // Verify distinct deterministic occurrence IDs
        assertNotEquals("Occurrence IDs must be distinct!", occA.occurrenceId, occB.occurrenceId)
        assertEquals("occ_sched-A-gap-230am_2026-03-08_02:30", occA.occurrenceId)
        assertEquals("occ_sched-B-fixed-300am_2026-03-08_03:00", occB.occurrenceId)

        // 2. Reconcile both schedules together via ScheduleReconciler
        val testClock = TestClock(
            currentInstant = transitionDate.atStartOfDay(nyZone).toInstant(), // Before both doses
            currentZone = nyZone
        )
        val reconciler = ScheduleReconciler(clock = testClock)

        val reconciliationResult = reconciler.reconcile(
            schedules = listOf(scheduleA, scheduleB),
            existingOccurrences = emptyList()
        )

        // 3. Verify neither was dropped or silently merged!
        val insertedOccs = reconciliationResult.occurrencesToInsert
        assertEquals("Both occurrences must be inserted without dropping either", 2, insertedOccs.size)

        val insertedIds = insertedOccs.map { it.id }.toSet()
        assertTrue("Inserted list must contain Schedule A occurrence", insertedIds.contains(occA.occurrenceId))
        assertTrue("Inserted list must contain Schedule B occurrence", insertedIds.contains(occB.occurrenceId))

        // Check medicine IDs are intact
        val insertedMeds = insertedOccs.map { it.medicineId }.toSet()
        assertEquals(setOf("med-aspirin", "med-blood-pressure"), insertedMeds)

        // Next alarm trigger points to the exact collision instant (both are due then)
        assertEquals(occA.triggerInstant, reconciliationResult.nextAlarmTrigger)

        println("=== DST BOUNDARY COLLISION VERIFIED ===")
        println("Schedule A ID: ${occA.occurrenceId} -> Intended: ${occA.intendedLocalTime}, Trigger: ${occA.triggerInstant}")
        println("Schedule B ID: ${occB.occurrenceId} -> Intended: ${occB.intendedLocalTime}, Trigger: ${occB.triggerInstant}")
        println("Collision trigger instant: ${occA.triggerInstant}")
        println("Reconciliation status: BOTH PRESERVED, ZERO DROPPED, DISTINCT OCCURRENCE IDs")
        println("=======================================")
    }
}
