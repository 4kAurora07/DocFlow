package com.doseflow.app.platform.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.data.local.AppDatabase
import com.doseflow.app.data.local.entity.DoseOccurrenceEntity
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.model.SystemAppClock
import com.doseflow.app.domain.scheduling.ScheduleReconciler
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndReconcileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Reconciliation broadcast received: action=$action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == Intent.ACTION_USER_PRESENT) {
                    handleUserUnlock(context)
                } else {
                    reconcileAndReschedule(context, action)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing reconciliation for $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootAndReconcile"

        suspend fun reconcileAndReschedule(context: Context, reason: String) {
            val db = AppDatabase.getInstance(context)
            val alarmScheduler = AlarmScheduler(context)

            val scheduleEntities = db.scheduleDao().getAllActiveSchedules()
            val schedules = scheduleEntities.map { it.toDomain() }

            val occurrenceEntities = db.doseOccurrenceDao().getAllOccurrences()
            val occurrences = occurrenceEntities.map { it.toDomain() }

            val clock = SystemAppClock()
            val reconciler = ScheduleReconciler(clock = clock)

            val result = reconciler.reconcile(
                schedules = schedules,
                existingOccurrences = occurrences
            )

            // Persist newly computed occurrences
            if (result.occurrencesToInsert.isNotEmpty()) {
                val toInsertEntities = result.occurrencesToInsert.map { DoseOccurrenceEntity.fromDomain(it) }
                db.doseOccurrenceDao().insertOccurrences(toInsertEntities)
                Log.i(TAG, "Inserted ${toInsertEntities.size} reconciled occurrences.")
            }

            // Persist state updates (such as auto-missed transitions)
            if (result.occurrencesToUpdate.isNotEmpty()) {
                val toUpdateEntities = result.occurrencesToUpdate.map { DoseOccurrenceEntity.fromDomain(it) }
                db.doseOccurrenceDao().updateOccurrences(toUpdateEntities)
                Log.i(TAG, "Updated ${toUpdateEntities.size} reconciled occurrences (autoMissed=${result.autoMarkedMissedCount}).")
            }

            // Fire immediate notifications for catch-up doses within grace window
            for (alertOcc in result.immediateAlertOccurrences) {
                val med = db.medicineDao().getMedicineById(alertOcc.medicineId)
                NotificationHelper.postDoseNotification(
                    context = context,
                    occurrenceId = alertOcc.id,
                    medicineName = med?.name ?: "Medicine",
                    dosage = med?.dosage ?: "",
                    unit = med?.unit ?: "",
                    instructions = med?.instructions ?: "",
                    scheduledEpochMillis = alertOcc.scheduledTime.toEpochMilli(),
                    isEscalation = false
                )
            }

            // Schedule the next upcoming alarm
            AlarmReceiver.rescheduleNextChainedAlarm(db, alarmScheduler)
            Log.i(TAG, "Reconciliation completed successfully for reason='$reason'.")
        }

        private suspend fun handleUserUnlock(context: Context) {
            val repository = DoseFlowRepository(context)
            if (!repository.isHomeScreenPopupEnabled()) return

            val db = AppDatabase.getInstance(context)
            val now = System.currentTimeMillis()
            val occurrences = db.doseOccurrenceDao().getAllOccurrences()
            val overdueDose = occurrences.firstOrNull { occ ->
                (occ.status == DoseStatus.PENDING.name || occ.status == DoseStatus.SNOOZED.name) &&
                now >= occ.scheduledEpochMillis &&
                (now - occ.scheduledEpochMillis) < 3 * 60 * 60 * 1000L // within 3 hours grace window
            }

            if (overdueDose != null) {
                val med = db.medicineDao().getMedicineById(overdueDose.medicineId)
                com.doseflow.app.ui.popup.DoseReminderPopupActivity.launch(
                    context = context,
                    occurrenceId = overdueDose.id,
                    medicineName = med?.name ?: "Medicine",
                    dosage = med?.dosage ?: "",
                    unit = med?.unit ?: "",
                    instructions = med?.instructions ?: "",
                    scheduledEpochMillis = overdueDose.scheduledEpochMillis
                )
            }
        }
    }
}

