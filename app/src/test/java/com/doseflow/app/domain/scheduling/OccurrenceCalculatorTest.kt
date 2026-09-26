package com.doseflow.app.domain.scheduling

import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class OccurrenceCalculatorTest {

    private val defaultZone = ZoneId.of("America/New_York")

    @Test
    fun dailyFixedTimes_singleTime_schedulesEveryDay() {
        val schedule = Schedule(
            id = "sched-daily-1",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 1, 3, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 1), occurrences[0].intendedLocalDate)
        assertEquals(LocalTime.of(8, 0), occurrences[0].intendedLocalTime)
        assertEquals(LocalDate.of(2026, 1, 2), occurrences[1].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 1, 3), occurrences[2].intendedLocalDate)
    }

    @Test
    fun dailyFixedTimes_multipleTimesPerDay_schedulesInOrder() {
        val schedule = Schedule(
            id = "sched-daily-multi",
            medicineId = "med-1",
            rule = RecurrenceRule.DailyFixedTimes(
                listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
            ),
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 1, 1, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(3, occurrences.size)
        assertEquals(LocalTime.of(8, 0), occurrences[0].intendedLocalTime)
        assertEquals(LocalTime.of(14, 0), occurrences[1].intendedLocalTime)
        assertEquals(LocalTime.of(20, 0), occurrences[2].intendedLocalTime)
    }

    @Test
    fun daysOfWeek_selectedDays_onlySchedulesOnMatchingDays() {
        val schedule = Schedule(
            id = "sched-dow",
            medicineId = "med-1",
            rule = RecurrenceRule.DaysOfWeek(
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                timesOfDay = listOf(LocalTime.of(9, 0))
            ),
            startDate = LocalDate.of(2026, 1, 5), // Monday
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 1, 5, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 1, 11, 23, 59).atZone(defaultZone).toInstant() // 1 week

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(3, occurrences.size)
        assertEquals(DayOfWeek.MONDAY, occurrences[0].intendedLocalDate.dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, occurrences[1].intendedLocalDate.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, occurrences[2].intendedLocalDate.dayOfWeek)
    }

    @Test
    fun everyNDays_schedulesAtDefinedIntervalFromAnchor() {
        val anchorDate = LocalDate.of(2026, 1, 1)
        val schedule = Schedule(
            id = "sched-every-3-days",
            medicineId = "med-1",
            rule = RecurrenceRule.EveryNDays(
                intervalDays = 3,
                anchorDate = anchorDate,
                timesOfDay = listOf(LocalTime.of(10, 0))
            ),
            startDate = anchorDate,
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 1, 10, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(4, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 1), occurrences[0].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 1, 4), occurrences[1].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 1, 7), occurrences[2].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 1, 10), occurrences[3].intendedLocalDate)
    }

    @Test
    fun oneOff_schedulesSingleTargetTimeOnly() {
        val targetDateTime = LocalDateTime.of(2026, 2, 14, 15, 30)
        val schedule = Schedule(
            id = "sched-one-off",
            medicineId = "med-1",
            rule = RecurrenceRule.OneOff(targetDateTime),
            startDate = targetDateTime.toLocalDate(),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 2, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 2, 28, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 2, 14), occurrences[0].intendedLocalDate)
        assertEquals(LocalTime.of(15, 30), occurrences[0].intendedLocalTime)
    }

    @Test
    fun asNeeded_producesZeroScheduledOccurrences() {
        val schedule = Schedule(
            id = "sched-prn",
            medicineId = "med-prn",
            rule = RecurrenceRule.AsNeeded,
            startDate = LocalDate.of(2026, 1, 1),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 1, 31, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun courseBoundaries_startAndEndInclusive_respectsExactEdges() {
        val schedule = Schedule(
            id = "sched-course",
            medicineId = "med-course",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 5, 10),
            endDate = LocalDate.of(2026, 5, 12),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 5, 8, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 5, 15, 23, 59).atZone(defaultZone).toInstant()

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 5, 10), occurrences[0].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 5, 11), occurrences[1].intendedLocalDate)
        assertEquals(LocalDate.of(2026, 5, 12), occurrences[2].intendedLocalDate)
    }

    @Test
    fun monthAndYearBoundaries_crossesYearAndLeapDayCorrectly() {
        // Test cross-year transition: Dec 31, 2027 to Jan 2, 2028
        val yearCrossingSchedule = Schedule(
            id = "sched-year",
            medicineId = "med-year",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(12, 0))),
            startDate = LocalDate.of(2027, 12, 30),
            timeZoneId = defaultZone
        )

        val yearWindowStart = LocalDateTime.of(2027, 12, 31, 0, 0).atZone(defaultZone).toInstant()
        val yearWindowEnd = LocalDateTime.of(2028, 1, 2, 23, 59).atZone(defaultZone).toInstant()

        val yearOccs = OccurrenceCalculator.calculateOccurrences(
            yearCrossingSchedule, yearWindowStart, yearWindowEnd, defaultZone
        )

        assertEquals(3, yearOccs.size)
        assertEquals(LocalDate.of(2027, 12, 31), yearOccs[0].intendedLocalDate)
        assertEquals(LocalDate.of(2028, 1, 1), yearOccs[1].intendedLocalDate)
        assertEquals(LocalDate.of(2028, 1, 2), yearOccs[2].intendedLocalDate)

        // Test leap year: 2028 is a leap year (Feb 28, Feb 29, March 1)
        val leapSchedule = Schedule(
            id = "sched-leap",
            medicineId = "med-leap",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.of(2028, 2, 27),
            timeZoneId = defaultZone
        )

        val leapWindowStart = LocalDateTime.of(2028, 2, 28, 0, 0).atZone(defaultZone).toInstant()
        val leapWindowEnd = LocalDateTime.of(2028, 3, 1, 23, 59).atZone(defaultZone).toInstant()

        val leapOccs = OccurrenceCalculator.calculateOccurrences(
            leapSchedule, leapWindowStart, leapWindowEnd, defaultZone
        )

        assertEquals(3, leapOccs.size)
        assertEquals(LocalDate.of(2028, 2, 28), leapOccs[0].intendedLocalDate)
        assertEquals(LocalDate.of(2028, 2, 29), leapOccs[1].intendedLocalDate) // Leap day!
        assertEquals(LocalDate.of(2028, 3, 1), leapOccs[2].intendedLocalDate)
    }

    @Test
    fun independentAndOverlappingSchedules_forOneMedicine_preserveDistinctIdentities() {
        val morningSchedule = Schedule(
            id = "sched-morning",
            medicineId = "med-duo",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(8, 0))),
            startDate = LocalDate.of(2026, 3, 1),
            timeZoneId = defaultZone
        )

        val eveningSchedule = Schedule(
            id = "sched-evening",
            medicineId = "med-duo",
            rule = RecurrenceRule.DailyFixedTimes(listOf(LocalTime.of(20, 0))),
            startDate = LocalDate.of(2026, 3, 1),
            timeZoneId = defaultZone
        )

        val windowStart = LocalDateTime.of(2026, 3, 1, 0, 0).atZone(defaultZone).toInstant()
        val windowEnd = LocalDateTime.of(2026, 3, 1, 23, 59).atZone(defaultZone).toInstant()

        val morningOccs = OccurrenceCalculator.calculateOccurrences(
            morningSchedule, windowStart, windowEnd, defaultZone
        )
        val eveningOccs = OccurrenceCalculator.calculateOccurrences(
            eveningSchedule, windowStart, windowEnd, defaultZone
        )

        val allOccs = (morningOccs + eveningOccs).sortedBy { it.triggerInstant }

        assertEquals(2, allOccs.size)
        assertNotEquals(allOccs[0].occurrenceId, allOccs[1].occurrenceId)
        assertEquals("med-duo", allOccs[0].medicineId)
        assertEquals("med-duo", allOccs[1].medicineId)
        assertEquals("sched-morning", allOccs[0].scheduleId)
        assertEquals("sched-evening", allOccs[1].scheduleId)
        assertEquals(LocalTime.of(8, 0), allOccs[0].intendedLocalTime)
        assertEquals(LocalTime.of(20, 0), allOccs[1].intendedLocalTime)
    }

    @Test
    fun hourlyInterval_over7DaySimulatedPeriod_reportsZeroDrift() {
        // Schedule every 8 hours for 7 full days (168 hours = 21 intervals)
        val anchorDateTime = LocalDateTime.of(2026, 4, 1, 8, 0)
        val anchorInstant = anchorDateTime.atZone(defaultZone).toInstant()
        val intervalHours = 8

        val schedule = Schedule(
            id = "sched-interval-8h",
            medicineId = "med-interval",
            rule = RecurrenceRule.HourlyInterval(
                intervalHours = intervalHours,
                anchorInstant = anchorInstant
            ),
            startDate = anchorDateTime.toLocalDate(),
            timeZoneId = defaultZone
        )

        val windowStart = anchorInstant
        val windowEnd = anchorInstant.plus(Duration.ofDays(7)) // 168 hours

        val occurrences = OccurrenceCalculator.calculateOccurrences(
            schedule, windowStart, windowEnd, defaultZone
        )

        // Expected occurrences: k = 0, 1, 2, ... 21 -> 22 occurrences total (from 0 to 168 hours inclusive)
        assertEquals(22, occurrences.size)

        var maxDriftNanos = 0L
        var totalDriftNanos = 0L

        for (k in occurrences.indices) {
            val occ = occurrences[k]
            val expectedInstant = anchorInstant.plus(Duration.ofHours(k.toLong() * intervalHours))
            val actualInstant = occ.triggerInstant

            val driftNanos = Math.abs(Duration.between(expectedInstant, actualInstant).toNanos())
            if (driftNanos > maxDriftNanos) {
                maxDriftNanos = driftNanos
            }
            totalDriftNanos += driftNanos

            assertEquals("Occurrence $k occurrenceId", "occ_sched-interval-8h_idx_$k", occ.occurrenceId)
            assertEquals("Occurrence $k triggerInstant", expectedInstant, actualInstant)
        }

        val averageDriftNanos = totalDriftNanos.toDouble() / occurrences.size

        // Explicitly report the exact computed drift as required by the user instructions
        println("=== 7-DAY INTERVAL DRIFT REPORT ===")
        println("Interval: Every $intervalHours hours over 7 days (168 hours)")
        println("Occurrences evaluated: ${occurrences.size}")
        println("Maximum computed drift: $maxDriftNanos nanoseconds (0.000 ms)")
        println("Average computed drift: $averageDriftNanos nanoseconds (0.000 ms)")
        println("Zero-drift property verified: PERFECTLY DERIVED FROM ANCHOR")
        println("===================================")

        assertEquals("Maximum computed drift must be exactly 0 nanoseconds", 0L, maxDriftNanos)
        assertEquals("Total computed drift must be exactly 0 nanoseconds", 0L, totalDriftNanos)
    }
}
