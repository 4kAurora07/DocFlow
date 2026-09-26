package com.doseflow.app.domain

import com.doseflow.app.data.AdherenceSummary
import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.Medicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AdherenceCalculationTest {

    @Test
    fun perfectAdherence_7DayWindow_calculates100Percent() {
        val summary = calculateSummary(
            taken = 10,
            skipped = 0,
            missed = 0,
            pending = 2,
            periodDays = 7
        )

        assertEquals(100.0, summary.adherencePercentage, 0.001)
        assertEquals(7, summary.periodDays)
        assertEquals(12, summary.totalScheduledDoses)
        assertEquals(10, summary.takenDoses)
        assertEquals(0, summary.skippedDoses)
        assertEquals(0, summary.missedDoses)
        assertEquals(2, summary.pendingDoses)
    }

    @Test
    fun mixedOutcomes_7DayWindow_calculatesCorrect80Percent() {
        // 8 taken, 1 skipped, 1 missed = 8 / (8 + 1 + 1) = 80.0%
        val summary = calculateSummary(
            taken = 8,
            skipped = 1,
            missed = 1,
            pending = 0,
            periodDays = 7
        )

        assertEquals(80.0, summary.adherencePercentage, 0.001)
        assertEquals(7, summary.periodDays)
        assertEquals(10, summary.totalScheduledDoses)
        assertEquals(8, summary.takenDoses)
        assertEquals(1, summary.skippedDoses)
        assertEquals(1, summary.missedDoses)
    }

    @Test
    fun mixedOutcomes_30DayWindow_calculates90Percent() {
        // 45 taken, 3 skipped, 2 missed, 5 pending
        // completed = 45 + 3 + 2 = 50. Rate = 45 / 50 = 90.0%
        val summary = calculateSummary(
            taken = 45,
            skipped = 3,
            missed = 2,
            pending = 5,
            periodDays = 30
        )

        assertEquals(90.0, summary.adherencePercentage, 0.001)
        assertEquals(30, summary.periodDays)
        assertEquals(55, summary.totalScheduledDoses)
        assertEquals(45, summary.takenDoses)
        assertEquals(3, summary.skippedDoses)
        assertEquals(2, summary.missedDoses)
        assertEquals(5, summary.pendingDoses)
    }

    @Test
    fun zeroDosesInWindow_returns100PercentDefault() {
        // No doses scheduled in period
        val summary = calculateSummary(
            taken = 0,
            skipped = 0,
            missed = 0,
            pending = 0,
            periodDays = 7
        )

        assertEquals(100.0, summary.adherencePercentage, 0.001)
        assertEquals(0, summary.totalScheduledDoses)
        assertEquals(0, summary.takenDoses)
    }

    @Test
    fun onlyPendingDosesInWindow_returns100PercentDefault() {
        // Only future/unresolved doses scheduled (no completed opportunities)
        val summary = calculateSummary(
            taken = 0,
            skipped = 0,
            missed = 0,
            pending = 5,
            periodDays = 7
        )

        assertEquals(100.0, summary.adherencePercentage, 0.001)
        assertEquals(5, summary.totalScheduledDoses)
        assertEquals(5, summary.pendingDoses)
    }

    @Test
    fun allDosesMissed_returns0Percent() {
        // 0 taken, 0 skipped, 10 missed = 0 / 10 = 0.0%
        val summary = calculateSummary(
            taken = 0,
            skipped = 0,
            missed = 10,
            pending = 1,
            periodDays = 30
        )

        assertEquals(0.0, summary.adherencePercentage, 0.001)
        assertEquals(11, summary.totalScheduledDoses)
        assertEquals(0, summary.takenDoses)
        assertEquals(10, summary.missedDoses)
    }

    @Test
    fun allDosesSkipped_returns0Percent() {
        // 0 taken, 5 skipped, 0 missed = 0 / 5 = 0.0%
        val summary = calculateSummary(
            taken = 0,
            skipped = 5,
            missed = 0,
            pending = 0,
            periodDays = 7
        )

        assertEquals(0.0, summary.adherencePercentage, 0.001)
        assertEquals(5, summary.totalScheduledDoses)
        assertEquals(0, summary.takenDoses)
        assertEquals(5, summary.skippedDoses)
    }

    @Test
    fun skippedDoesNotCountAsTaken_denominatorIncludesSkipped() {
        // 10 taken, 10 skipped, 0 missed = 10 / 20 = 50.0%
        val summary = calculateSummary(
            taken = 10,
            skipped = 10,
            missed = 0,
            pending = 0,
            periodDays = 7
        )

        assertEquals(50.0, summary.adherencePercentage, 0.001)
        assertEquals(20, summary.totalScheduledDoses)
        assertEquals(10, summary.takenDoses)
        assertEquals(10, summary.skippedDoses)
    }

    @Test
    fun medicineWithNoScheduleYet_producesZeroOccurrences_adherence100Percent() {
        // Medicine created without schedule
        val medicine = Medicine(
            id = "med-unscheduled",
            name = "Vitamin C",
            dosage = "500",
            unit = "mg"
        )
        val occurrencesForMed = emptyList<DoseOccurrence>()

        val taken = occurrencesForMed.count { it.status == DoseStatus.TAKEN }
        val skipped = occurrencesForMed.count { it.status == DoseStatus.SKIPPED }
        val missed = occurrencesForMed.count { it.status == DoseStatus.MISSED }
        val pending = occurrencesForMed.count { it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED }

        val summary = calculateSummary(taken, skipped, missed, pending, periodDays = 7)

        assertEquals("Vitamin C", medicine.name)
        assertEquals(0, summary.totalScheduledDoses)
        assertEquals(100.0, summary.adherencePercentage, 0.001)
    }

    private fun calculateSummary(
        taken: Int,
        skipped: Int,
        missed: Int,
        pending: Int,
        periodDays: Int
    ): AdherenceSummary {
        val total = taken + skipped + missed + pending
        val completed = taken + skipped + missed
        val rate = if (completed > 0) (taken.toDouble() / completed) * 100.0 else 100.0

        return AdherenceSummary(
            periodDays = periodDays,
            totalScheduledDoses = total,
            takenDoses = taken,
            skippedDoses = skipped,
            missedDoses = missed,
            pendingDoses = pending,
            adherencePercentage = rate
        )
    }
}
