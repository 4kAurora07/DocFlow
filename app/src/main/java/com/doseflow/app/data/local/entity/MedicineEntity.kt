package com.doseflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.doseflow.app.domain.model.Medicine
import java.time.Instant

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val dosage: String,
    val unit: String,
    val instructions: String,
    val isArchived: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    fun toDomain(): Medicine = Medicine(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit,
        instructions = instructions,
        isArchived = isArchived,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

    companion object {
        fun fromDomain(medicine: Medicine): MedicineEntity = MedicineEntity(
            id = medicine.id,
            name = medicine.name,
            dosage = medicine.dosage,
            unit = medicine.unit,
            instructions = medicine.instructions,
            isArchived = medicine.isArchived,
            createdAtEpochMillis = medicine.createdAt.toEpochMilli(),
            updatedAtEpochMillis = medicine.updatedAt.toEpochMilli()
        )
    }
}
