package com.doseflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.doseflow.app.data.local.entity.OutboxEventEntity

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 50): List<OutboxEventEntity>

    @Query("SELECT * FROM outbox_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): OutboxEventEntity?

    @Query("SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'")
    suspend fun getPendingEventCount(): Int

    @Query("SELECT * FROM outbox_events ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    suspend fun getAllEvents(limit: Int = 100): List<OutboxEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OutboxEventEntity)

    @Update
    suspend fun updateEvent(event: OutboxEventEntity)

    @Query("DELETE FROM outbox_events WHERE id = :id")
    suspend fun deleteEvent(id: String)
}
