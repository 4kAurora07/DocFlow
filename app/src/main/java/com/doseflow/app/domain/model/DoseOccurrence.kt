package com.doseflow.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class DoseStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    MISSED,
    SNOOZED
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

data class DoseOccurrence(
    val id: String,
    val scheduleId: String,
    val medicineId: String,
    val scheduledTime: Instant,
    val intendedLocalDate: LocalDate,
    val intendedLocalTime: LocalTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val actionTimestamp: Instant? = null,
    val takenTimestamp: Instant? = null,
    val snoozedUntil: Instant? = null,
    val escalationCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Instant = Instant.now()
) {
    val isActioned: Boolean
        get() = status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED || status == DoseStatus.MISSED
}
