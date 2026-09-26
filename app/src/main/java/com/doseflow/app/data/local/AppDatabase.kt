package com.doseflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.doseflow.app.data.local.dao.DoseOccurrenceDao
import com.doseflow.app.data.local.dao.MedicineDao
import com.doseflow.app.data.local.dao.OutboxDao
import com.doseflow.app.data.local.dao.ScheduleDao
import com.doseflow.app.data.local.entity.DoseOccurrenceEntity
import com.doseflow.app.data.local.entity.MedicineEntity
import com.doseflow.app.data.local.entity.OutboxEventEntity
import com.doseflow.app.data.local.entity.ScheduleEntity

@Database(
    entities = [
        MedicineEntity::class,
        ScheduleEntity::class,
        DoseOccurrenceEntity::class,
        OutboxEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseOccurrenceDao(): DoseOccurrenceDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        private const val DATABASE_NAME = "doseflow.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
