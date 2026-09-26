package com.doseflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.doseflow.app.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE medicineId = :medicineId ORDER BY createdAtEpochMillis ASC")
    fun getSchedulesForMedicine(medicineId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    suspend fun getAllActiveSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    fun getAllActiveSchedulesFlow(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET isActive = 0, updatedAtEpochMillis = :updatedAt WHERE medicineId = :medicineId")
    suspend fun deactivateSchedulesForMedicine(medicineId: String, updatedAt: Long)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: String)
}
