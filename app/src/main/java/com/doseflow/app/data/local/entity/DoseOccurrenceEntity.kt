package com.doseflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "dose_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("scheduleId"),
        Index("medicineId"),
        Index("scheduledEpochMillis"),
        Index("status")
    ]
)
data class DoseOccurrenceEntity(
    @PrimaryKey
    val id: String,
    val scheduleId: String,
    val medicineId: String,
    val scheduledEpochMillis: Long,
    val intendedLocalDateIso: String,
    val intendedLocalTimeIso: String,
    val status: String,
    val actionEpochMillis: Long?,
    val takenEpochMillis: Long?,
    val snoozedUntilEpochMillis: Long?,
    val escalationCount: Int,
    val syncStatus: String,
    val updatedAtEpochMillis: Long
) {
    fun toDomain(): DoseOccurrence = DoseOccurrence(
        id = id,
        scheduleId = scheduleId,
        medicineId = medicineId,
        scheduledTime = Instant.ofEpochMilli(scheduledEpochMillis),
        intendedLocalDate = LocalDate.parse(intendedLocalDateIso),
        intendedLocalTime = LocalTime.parse(intendedLocalTimeIso),
        status = DoseStatus.valueOf(status),
        actionTimestamp = actionEpochMillis?.let { Instant.ofEpochMilli(it) },
        takenTimestamp = takenEpochMillis?.let { Instant.ofEpochMilli(it) },
        snoozedUntil = snoozedUntilEpochMillis?.let { Instant.ofEpochMilli(it) },
        escalationCount = escalationCount,
        syncStatus = SyncStatus.valueOf(syncStatus),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

    companion object {
        fun fromDomain(occurrence: DoseOccurrence): DoseOccurrenceEntity = DoseOccurrenceEntity(
            id = occurrence.id,
            scheduleId = occurrence.scheduleId,
            medicineId = occurrence.medicineId,
            scheduledEpochMillis = occurrence.scheduledTime.toEpochMilli(),
            intendedLocalDateIso = occurrence.intendedLocalDate.toString(),
            intendedLocalTimeIso = occurrence.intendedLocalTime.toString(),
            status = occurrence.status.name,
            actionEpochMillis = occurrence.actionTimestamp?.toEpochMilli(),
            takenEpochMillis = occurrence.takenTimestamp?.toEpochMilli(),
            snoozedUntilEpochMillis = occurrence.snoozedUntil?.toEpochMilli(),
            escalationCount = occurrence.escalationCount,
            syncStatus = occurrence.syncStatus.name,
            updatedAtEpochMillis = occurrence.updatedAt.toEpochMilli()
        )
    }
}
