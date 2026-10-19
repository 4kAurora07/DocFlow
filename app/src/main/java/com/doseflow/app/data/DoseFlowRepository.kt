package com.doseflow.app.data

import android.content.Context
import com.doseflow.app.data.local.AppDatabase
import com.doseflow.app.data.local.entity.DoseOccurrenceEntity
import com.doseflow.app.data.local.entity.MedicineEntity
import com.doseflow.app.data.local.entity.ScheduleEntity
import com.doseflow.app.domain.model.*
import com.doseflow.app.domain.scheduling.OccurrenceCalculator
import com.doseflow.app.domain.scheduling.ScheduleReconciler
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.notification.NotificationHelper
import com.doseflow.app.platform.receiver.AlarmReceiver
import com.doseflow.app.platform.receiver.BootAndReconcileReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DoseWithMedicine(
    val occurrence: DoseOccurrence,
    val medicine: Medicine
)

data class AdherenceSummary(
    val periodDays: Int,
    val totalScheduledDoses: Int,
    val takenDoses: Int,
    val skippedDoses: Int,
    val missedDoses: Int,
    val pendingDoses: Int,
    val adherencePercentage: Double
)

class DoseFlowRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val alarmScheduler = AlarmScheduler(context)

    fun getActiveMedicines(): Flow<List<Medicine>> {
        return db.medicineDao().getActiveMedicines().map { list -> list.map { it.toDomain() } }
    }

    fun getAllMedicines(): Flow<List<Medicine>> {
        return db.medicineDao().getAllMedicines().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getMedicineById(id: String): Medicine? = withContext(Dispatchers.IO) {
        db.medicineDao().getMedicineById(id)?.toDomain()
    }

    suspend fun saveMedicine(medicine: Medicine) = withContext(Dispatchers.IO) {
        db.medicineDao().insertMedicine(MedicineEntity.fromDomain(medicine))
        try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
    }

    suspend fun setMedicineArchived(id: String, isArchived: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.medicineDao().setArchived(id, isArchived, now)
        if (isArchived) {
            db.scheduleDao().deactivateSchedulesForMedicine(id, now)
            reconcileAllSchedules("archive_medicine")
        }
        try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
    }

    suspend fun deleteMedicine(id: String) = withContext(Dispatchers.IO) {
        // Transactional delete: schedules & pending occurrences deleted via CASCADE
        db.medicineDao().deleteMedicine(id)
        reconcileAllSchedules("delete_medicine")
        try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
    }

    fun getSchedulesForMedicine(medicineId: String): Flow<List<Schedule>> {
        return db.scheduleDao().getSchedulesForMedicine(medicineId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun saveSchedule(schedule: Schedule) = withContext(Dispatchers.IO) {
        db.scheduleDao().insertSchedule(ScheduleEntity.fromDomain(schedule))
        reconcileAllSchedules("save_schedule")
        try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
    }

    suspend fun deleteSchedule(scheduleId: String) = withContext(Dispatchers.IO) {
        alarmScheduler.cancelDoseAlarms(scheduleId)
        db.scheduleDao().deleteSchedule(scheduleId)
        reconcileAllSchedules("delete_schedule")
        try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
    }

    fun getTodayDoses(): Flow<List<DoseWithMedicine>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDayEpoch = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDayEpoch = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val occurrencesFlow = db.doseOccurrenceDao().getOccurrencesBetweenFlow(startOfDayEpoch, endOfDayEpoch)
        val medicinesFlow = db.medicineDao().getAllMedicines()

        return combine(occurrencesFlow, medicinesFlow) { occurrences, medicines ->
            val medMap = medicines.associateBy { it.id }
            occurrences.mapNotNull { occEntity ->
                val occ = occEntity.toDomain()
                val med = medMap[occ.medicineId]?.toDomain() ?: Medicine(
                    id = occ.medicineId,
                    name = "Medication",
                    dosage = "",
                    unit = ""
                )
                DoseWithMedicine(occ, med)
            }
        }
    }

    suspend fun markTaken(occurrenceId: String) = withContext(Dispatchers.IO) {
        NotificationHelper.dismissNotification(context, occurrenceId)
        alarmScheduler.cancelDoseAlarms(occurrenceId)

        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occurrence != null) {
            val nowEpoch = System.currentTimeMillis()
            val updated = occurrence.copy(
                status = DoseStatus.TAKEN.name,
                takenEpochMillis = nowEpoch,
                actionEpochMillis = nowEpoch,
                updatedAtEpochMillis = nowEpoch
            )
            db.doseOccurrenceDao().updateOccurrence(updated)
            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_TAKEN",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"TAKEN","timestamp":$nowEpoch}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
            try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
        }
        AlarmReceiver.rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    suspend fun markSkipped(occurrenceId: String) = withContext(Dispatchers.IO) {
        NotificationHelper.dismissNotification(context, occurrenceId)
        alarmScheduler.cancelDoseAlarms(occurrenceId)

        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occurrence != null) {
            val nowEpoch = System.currentTimeMillis()
            val updated = occurrence.copy(
                status = DoseStatus.SKIPPED.name,
                actionEpochMillis = nowEpoch,
                updatedAtEpochMillis = nowEpoch
            )
            db.doseOccurrenceDao().updateOccurrence(updated)
            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_SKIPPED",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"SKIPPED","timestamp":$nowEpoch}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
            try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
        }
        AlarmReceiver.rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    suspend fun snoozeDose(occurrenceId: String) = withContext(Dispatchers.IO) {
        NotificationHelper.dismissNotification(context, occurrenceId)
        alarmScheduler.cancelDoseAlarms(occurrenceId)

        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occurrence != null) {
            val now = Instant.now()
            val snoozedUntil = now.plus(AlarmReceiver.SNOOZE_DURATION).toEpochMilli()

            val updated = occurrence.copy(
                status = DoseStatus.SNOOZED.name,
                snoozedUntilEpochMillis = snoozedUntil,
                updatedAtEpochMillis = now.toEpochMilli()
            )
            db.doseOccurrenceDao().updateOccurrence(updated)
            alarmScheduler.scheduleSnoozeAlarm(snoozedUntil, occurrenceId)
            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_SNOOZED",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"SNOOZED","snoozedUntil":$snoozedUntil,"timestamp":${now.toEpochMilli()}}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
            try { syncFullStateToCaregiverPortal() } catch (_: Exception) {}
        }
    }

    fun getAdherenceSummary(days: Int): Flow<AdherenceSummary> {
        val now = Instant.now()
        val startInstant = now.minus(Duration.ofDays(days.toLong()))
        val startEpoch = startInstant.toEpochMilli()
        val nowEpoch = now.toEpochMilli()

        return db.doseOccurrenceDao().getOccurrencesBetweenFlow(startEpoch, nowEpoch).map { list ->
            val pastDoses = list.map { it.toDomain() }

            val taken = pastDoses.count { it.status == DoseStatus.TAKEN }
            val skipped = pastDoses.count { it.status == DoseStatus.SKIPPED }
            val missed = pastDoses.count { it.status == DoseStatus.MISSED }
            val pending = pastDoses.count { it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED }

            val completedOpportunities = taken + skipped + missed
            val adherence = if (completedOpportunities > 0) {
                (taken.toDouble() / completedOpportunities) * 100.0
            } else 100.0

            AdherenceSummary(
                periodDays = days,
                totalScheduledDoses = pastDoses.size,
                takenDoses = taken,
                skippedDoses = skipped,
                missedDoses = missed,
                pendingDoses = pending,
                adherencePercentage = adherence
            )
        }
    }

    fun getRecentHistory(limit: Int = 100): Flow<List<DoseWithMedicine>> {
        val occurrencesFlow = db.doseOccurrenceDao().getRecentOccurrencesFlow(limit)
        val medicinesFlow = db.medicineDao().getAllMedicines()

        return combine(occurrencesFlow, medicinesFlow) { occurrences, medicines ->
            val medMap = medicines.associateBy { it.id }
            occurrences.mapNotNull { occEntity ->
                val occ = occEntity.toDomain()
                val med = medMap[occ.medicineId]?.toDomain() ?: Medicine(
                    id = occ.medicineId,
                    name = "Medication",
                    dosage = "",
                    unit = ""
                )
                DoseWithMedicine(occ, med)
            }
        }
    }

    suspend fun reconcileAllSchedules(reason: String) = withContext(Dispatchers.IO) {
        BootAndReconcileReceiver.reconcileAndReschedule(context, reason)
    }

    fun isOnboardingCompleted(): Boolean {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun getCaregiverCode(): String? {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        return prefs.getString("caregiver_pairing_code", null)
    }

    fun setCaregiverCode(code: String) {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("caregiver_pairing_code", code).apply()
    }

    fun getPatientId(): String {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        var pId = prefs.getString("patient_id", null)
        if (pId.isNullOrBlank()) {
            pId = "patient_" + java.util.UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("patient_id", pId).apply()
        }
        return pId
    }

    fun getPatientName(): String {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        return prefs.getString("patient_display_name", "My Android Device") ?: "My Android Device"
    }

    fun setPatientName(name: String) {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("patient_display_name", name.trim()).apply()
    }

    fun getServerUrl(): String {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("sync_server_url", "https://adhivio-med.onrender.com")
        if (saved == null || saved.contains("10.1.1.") || saved.contains("10.0.2.") || saved.contains("localhost")) {
            return "https://adhivio-med.onrender.com"
        }
        return saved
    }

    fun setServerUrl(url: String) {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_server_url", url.trim().trimEnd('/')).apply()
    }

    fun isHomeScreenPopupEnabled(): Boolean {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("home_screen_popup_enabled", true)
    }

    fun setHomeScreenPopupEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("doseflow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("home_screen_popup_enabled", enabled).apply()
    }


    suspend fun generateAndRegisterCaregiverCode(): String? = withContext(Dispatchers.IO) {
        val client = com.doseflow.app.data.sync.DefaultSyncApiClient(context)
        val patientId = getPatientId()
        val patientName = getPatientName()
        val code = client.generateCaregiverCode(patientId = patientId, patientName = patientName)
        if (code != null) {
            setCaregiverCode(code)
            syncFullStateToCaregiverPortal(code)
        }
        code
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val client = com.doseflow.app.data.sync.DefaultSyncApiClient(context)
        client.testConnection()
    }

    suspend fun syncFullStateToCaregiverPortal(pairingCode: String? = getCaregiverCode()): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = com.doseflow.app.data.sync.DefaultSyncApiClient(context)
            val medicines = db.medicineDao().getActiveMedicinesList()
            val occurrences = db.doseOccurrenceDao().getAllOccurrences()
            val patientId = getPatientId()
            val patientName = getPatientName()
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val timeZone = java.time.ZoneId.systemDefault().id

            val medJsonList = medicines.joinToString(",") { m ->
                """{"id":"${m.id}","medicineName":"${m.name}","dosage":"${m.dosage}","unit":"${m.unit}","instructions":"${m.instructions}"}"""
            }

            val occJsonList = occurrences.takeLast(50).joinToString(",") { o ->
                val medName = medicines.find { it.id == o.medicineId }?.name ?: "Medication"
                val medDose = medicines.find { it.id == o.medicineId }?.dosage ?: ""
                """{"id":"${o.id}","scheduleId":"${o.scheduleId}","medicineName":"$medName","dosage":"$medDose","scheduledTime":"${o.intendedLocalTimeIso}","scheduledEpochMillis":${o.scheduledEpochMillis},"status":"${o.status}","actionTimestamp":${o.actionEpochMillis ?: "null"}}"""
            }

            val payload = """
                {
                  "patientId": "$patientId",
                  "patientName": "$patientName",
                  "deviceName": "$deviceModel",
                  "timeZone": "$timeZone",
                  "pairingCode": ${if (pairingCode != null) "\"$pairingCode\"" else "null"},
                  "schedules": [$medJsonList],
                  "occurrences": [$occJsonList]
                }
            """.trimIndent()

            client.syncPatientState(payload)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun ensureCaregiverCodeAndSync(): String? = withContext(Dispatchers.IO) {
        var code = getCaregiverCode()
        if (code.isNullOrBlank()) {
            code = generateAndRegisterCaregiverCode()
            if (code == null) {
                // Generate a friendly 6-digit code locally so patient always has a code
                code = "DF-" + (1000..9999).random().toString()
                setCaregiverCode(code)
            }
        }
        syncFullStateToCaregiverPortal(code)
        code
    }
}

