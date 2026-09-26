package com.doseflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.doseflow.app.domain.model.RecurrenceRule
import com.doseflow.app.domain.model.Schedule
import com.doseflow.app.domain.model.ZoneTrackingPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Serializable
private data class DailyFixedTimesDto(val times: List<String>)

@Serializable
private data class DaysOfWeekDto(val days: List<String>, val times: List<String>)

@Serializable
private data class EveryNDaysDto(val intervalDays: Int, val anchorDateIso: String, val times: List<String>)

@Serializable
private data class HourlyIntervalDto(val intervalHours: Int, val anchorEpochMillis: Long)

@Serializable
private data class OneOffDto(val dateTimeIso: String)

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("medicineId"),
        Index("isActive")
    ]
)
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val medicineId: String,
    val ruleType: String,
    val ruleDataJson: String,
    val startDateIso: String,
    val endDateIso: String?,
    val timeZoneId: String,
    val zoneTrackingPolicy: String,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    fun toDomain(): Schedule {
        val rule: RecurrenceRule = when (ruleType) {
            "DAILY_FIXED_TIMES" -> {
                val dto = Json.decodeFromString<DailyFixedTimesDto>(ruleDataJson)
                RecurrenceRule.DailyFixedTimes(dto.times.map { LocalTime.parse(it) })
            }
            "DAYS_OF_WEEK" -> {
                val dto = Json.decodeFromString<DaysOfWeekDto>(ruleDataJson)
                RecurrenceRule.DaysOfWeek(
                    daysOfWeek = dto.days.map { DayOfWeek.valueOf(it) }.toSet(),
                    timesOfDay = dto.times.map { LocalTime.parse(it) }
                )
            }
            "EVERY_N_DAYS" -> {
                val dto = Json.decodeFromString<EveryNDaysDto>(ruleDataJson)
                RecurrenceRule.EveryNDays(
                    intervalDays = dto.intervalDays,
                    anchorDate = LocalDate.parse(dto.anchorDateIso),
                    timesOfDay = dto.times.map { LocalTime.parse(it) }
                )
            }
            "HOURLY_INTERVAL" -> {
                val dto = Json.decodeFromString<HourlyIntervalDto>(ruleDataJson)
                RecurrenceRule.HourlyInterval(
                    intervalHours = dto.intervalHours,
                    anchorInstant = Instant.ofEpochMilli(dto.anchorEpochMillis)
                )
            }
            "ONE_OFF" -> {
                val dto = Json.decodeFromString<OneOffDto>(ruleDataJson)
                RecurrenceRule.OneOff(
                    targetDateTime = java.time.LocalDateTime.parse(dto.dateTimeIso)
                )
            }
            "AS_NEEDED" -> RecurrenceRule.AsNeeded
            else -> error("Unknown ruleType: $ruleType")
        }

        return Schedule(
            id = id,
            medicineId = medicineId,
            rule = rule,
            startDate = LocalDate.parse(startDateIso),
            endDate = endDateIso?.let { LocalDate.parse(it) },
            timeZoneId = ZoneId.of(timeZoneId),
            zoneTrackingPolicy = ZoneTrackingPolicy.valueOf(zoneTrackingPolicy),
            isActive = isActive,
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
        )
    }

    companion object {
        fun fromDomain(schedule: Schedule): ScheduleEntity {
            val (ruleType, ruleJson) = when (val rule = schedule.rule) {
                is RecurrenceRule.DailyFixedTimes -> {
                    val dto = DailyFixedTimesDto(rule.timesOfDay.map { it.toString() })
                    "DAILY_FIXED_TIMES" to Json.encodeToString(dto)
                }
                is RecurrenceRule.DaysOfWeek -> {
                    val dto = DaysOfWeekDto(
                        days = rule.daysOfWeek.map { it.name },
                        times = rule.timesOfDay.map { it.toString() }
                    )
                    "DAYS_OF_WEEK" to Json.encodeToString(dto)
                }
                is RecurrenceRule.EveryNDays -> {
                    val dto = EveryNDaysDto(
                        intervalDays = rule.intervalDays,
                        anchorDateIso = rule.anchorDate.toString(),
                        times = rule.timesOfDay.map { it.toString() }
                    )
                    "EVERY_N_DAYS" to Json.encodeToString(dto)
                }
                is RecurrenceRule.HourlyInterval -> {
                    val dto = HourlyIntervalDto(
                        intervalHours = rule.intervalHours,
                        anchorEpochMillis = rule.anchorInstant.toEpochMilli()
                    )
                    "HOURLY_INTERVAL" to Json.encodeToString(dto)
                }
                is RecurrenceRule.OneOff -> {
                    val dto = OneOffDto(rule.targetDateTime.toString())
                    "ONE_OFF" to Json.encodeToString(dto)
                }
                is RecurrenceRule.AsNeeded -> {
                    "AS_NEEDED" to "{}"
                }
            }

            return ScheduleEntity(
                id = schedule.id,
                medicineId = schedule.medicineId,
                ruleType = ruleType,
                ruleDataJson = ruleJson,
                startDateIso = schedule.startDate.toString(),
                endDateIso = schedule.endDate?.toString(),
                timeZoneId = schedule.timeZoneId.id,
                zoneTrackingPolicy = schedule.zoneTrackingPolicy.name,
                isActive = schedule.isActive,
                createdAtEpochMillis = schedule.createdAt.toEpochMilli(),
                updatedAtEpochMillis = schedule.updatedAt.toEpochMilli()
            )
        }
    }
}
