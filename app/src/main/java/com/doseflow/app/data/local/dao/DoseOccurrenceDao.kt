package com.doseflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.doseflow.app.data.local.entity.DoseOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseOccurrenceDao {
    @Query("SELECT * FROM dose_occurrences WHERE scheduledEpochMillis >= :startEpochMillis AND scheduledEpochMillis <= :endEpochMillis ORDER BY scheduledEpochMillis ASC")
    fun getOccurrencesBetweenFlow(startEpochMillis: Long, endEpochMillis: Long): Flow<List<DoseOccurrenceEntity>>

    @Query("SELECT * FROM dose_occurrences WHERE scheduledEpochMillis >= :startEpochMillis AND scheduledEpochMillis <= :endEpochMillis ORDER BY scheduledEpochMillis ASC")
    suspend fun getOccurrencesBetween(startEpochMillis: Long, endEpochMillis: Long): List<DoseOccurrenceEntity>

    @Query("SELECT * FROM dose_occurrences WHERE status IN ('PENDING', 'SNOOZED') ORDER BY scheduledEpochMillis ASC")
    suspend fun getPendingOrSnoozedOccurrences(): List<DoseOccurrenceEntity>

    @Query("SELECT * FROM dose_occurrences WHERE id = :id LIMIT 1")
    suspend fun getOccurrenceById(id: String): DoseOccurrenceEntity?

    @Query("SELECT * FROM dose_occurrences WHERE medicineId = :medicineId ORDER BY scheduledEpochMillis DESC")
    fun getOccurrencesForMedicineFlow(medicineId: String): Flow<List<DoseOccurrenceEntity>>

    @Query("SELECT * FROM dose_occurrences ORDER BY scheduledEpochMillis ASC")
    suspend fun getAllOccurrences(): List<DoseOccurrenceEntity>

    @Query("SELECT * FROM dose_occurrences ORDER BY scheduledEpochMillis DESC LIMIT :limit")
    fun getRecentOccurrencesFlow(limit: Int = 100): Flow<List<DoseOccurrenceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(occurrences: List<DoseOccurrenceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: DoseOccurrenceEntity)

    @Update
    suspend fun updateOccurrence(occurrence: DoseOccurrenceEntity)

    @Update
    suspend fun updateOccurrences(occurrences: List<DoseOccurrenceEntity>)

    @Query("DELETE FROM dose_occurrences WHERE scheduleId = :scheduleId AND status = 'PENDING'")
    suspend fun deletePendingOccurrencesForSchedule(scheduleId: String)
}
