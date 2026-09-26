package com.doseflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_events",
    indices = [
        Index("status"),
        Index("createdAtEpochMillis")
    ]
)
data class OutboxEventEntity(
    @PrimaryKey
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptEpochMillis: Long? = null,
    val status: String = "PENDING"
)
