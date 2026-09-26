package com.doseflow.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doseflow.app.data.local.AppDatabase
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return

        Log.i(TAG, "Notification action received: action=$action for occurrence=$occurrenceId")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val alarmScheduler = AlarmScheduler(context)

                when (action) {
                    ACTION_TAKEN -> handleTaken(context, db, alarmScheduler, occurrenceId)
                    ACTION_SKIP -> handleSkip(context, db, alarmScheduler, occurrenceId)
                    ACTION_SNOOZE -> handleSnooze(context, db, alarmScheduler, occurrenceId)
                    else -> Log.w(TAG, "Unknown notification action: $action")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTaken(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
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
            Log.i(TAG, "Occurrence $occurrenceId successfully marked TAKEN at $nowEpoch")

            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_TAKEN",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"TAKEN","timestamp":$nowEpoch}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
        }

        AlarmReceiver.rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    private suspend fun handleSkip(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
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
            Log.i(TAG, "Occurrence $occurrenceId successfully marked SKIPPED at $nowEpoch")

            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_SKIPPED",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"SKIPPED","timestamp":$nowEpoch}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
        }

        AlarmReceiver.rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    private suspend fun handleSnooze(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
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

            // Schedule the 10-minute snooze alarm
            alarmScheduler.scheduleSnoozeAlarm(snoozedUntil, occurrenceId)
            Log.i(TAG, "Occurrence $occurrenceId snoozed until epoch $snoozedUntil")

            com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
                db = db,
                aggregateType = "DOSE_OCCURRENCE",
                aggregateId = occurrenceId,
                eventType = "DOSE_SNOOZED",
                payloadJson = """{"occurrenceId":"$occurrenceId","status":"SNOOZED","snoozedUntil":$snoozedUntil,"timestamp":${now.toEpochMilli()}}"""
            )
            com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"

        const val ACTION_TAKEN = "com.doseflow.app.ACTION_TAKEN"
        const val ACTION_SKIP = "com.doseflow.app.ACTION_SKIP"
        const val ACTION_SNOOZE = "com.doseflow.app.ACTION_SNOOZE"

        const val EXTRA_OCCURRENCE_ID = "EXTRA_OCCURRENCE_ID"
    }
}
