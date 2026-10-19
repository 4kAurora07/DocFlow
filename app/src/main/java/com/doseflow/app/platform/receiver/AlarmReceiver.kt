package com.doseflow.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doseflow.app.data.local.AppDatabase
import com.doseflow.app.data.local.entity.DoseOccurrenceEntity
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.domain.scheduling.OccurrenceCalculator
import com.doseflow.app.platform.alarm.AlarmScheduler
import com.doseflow.app.platform.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return

        Log.i(TAG, "onReceive action=$action for occurrenceId=$occurrenceId")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val nativeWakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        )?.apply {
            setReferenceCounted(false)
        }
        val wakeLockController = nativeWakeLock?.let { AndroidWakeLockController(it) }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeWithWakeLock(wakeLockController) {
                    val db = AppDatabase.getInstance(context)
                    val alarmScheduler = AlarmScheduler(context)

                    when (action) {
                        ACTION_DOSE_ALARM -> handleDoseAlarm(context, db, alarmScheduler, occurrenceId)
                        ACTION_ESCALATION_ALARM -> handleEscalationAlarm(context, db, alarmScheduler, occurrenceId)
                        ACTION_MISSED_ALARM -> handleMissedAlarm(context, db, alarmScheduler, occurrenceId)
                        ACTION_SNOOZE_ALARM -> handleSnoozeAlarm(context, db, alarmScheduler, occurrenceId)
                        else -> Log.w(TAG, "Unhandled action: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm $action for $occurrenceId: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDoseAlarm(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occurrence == null) {
            Log.w(TAG, "Occurrence $occurrenceId not found in database.")
            return
        }

        if (occurrence.status != DoseStatus.PENDING.name) {
            Log.i(TAG, "Occurrence $occurrenceId is in state ${occurrence.status}; skipping alarm.")
            return
        }

        val medicine = db.medicineDao().getMedicineById(occurrence.medicineId)
        val medicineName = medicine?.name ?: "Medicine"
        val dosage = medicine?.dosage ?: ""
        val unit = medicine?.unit ?: ""
        val instructions = medicine?.instructions ?: ""

        // Post high-priority notification
        NotificationHelper.postDoseNotification(
            context = context,
            occurrenceId = occurrenceId,
            medicineName = medicineName,
            dosage = dosage,
            unit = unit,
            instructions = instructions,
            scheduledEpochMillis = occurrence.scheduledEpochMillis,
            isEscalation = false
        )

        // Launch home screen popup if scheduled time has arrived or passed
        if (System.currentTimeMillis() >= occurrence.scheduledEpochMillis) {
            com.doseflow.app.ui.popup.DoseReminderPopupActivity.launch(
                context = context,
                occurrenceId = occurrenceId,
                medicineName = medicineName,
                dosage = dosage,
                unit = unit,
                instructions = instructions,
                scheduledEpochMillis = occurrence.scheduledEpochMillis
            )
        }

        // Schedule escalation in 15 minutes (default)
        val now = Instant.now()
        val escalationTime = now.plus(DEFAULT_ESCALATION_DELAY).toEpochMilli()
        alarmScheduler.scheduleEscalationAlarm(escalationTime, occurrenceId)

        // Schedule next chained alarm for future doses
        rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    private suspend fun handleEscalationAlarm(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId) ?: return
        if (occurrence.status != DoseStatus.PENDING.name) {
            Log.i(TAG, "Occurrence $occurrenceId is no longer pending (${occurrence.status}); canceling escalation.")
            return
        }

        val updatedEscalation = occurrence.copy(
            escalationCount = occurrence.escalationCount + 1,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        db.doseOccurrenceDao().updateOccurrence(updatedEscalation)

        val medicine = db.medicineDao().getMedicineById(occurrence.medicineId)
        NotificationHelper.postDoseNotification(
            context = context,
            occurrenceId = occurrenceId,
            medicineName = medicine?.name ?: "Medicine",
            dosage = medicine?.dosage ?: "",
            unit = medicine?.unit ?: "",
            instructions = medicine?.instructions ?: "",
            scheduledEpochMillis = occurrence.scheduledEpochMillis,
            isEscalation = true
        )

        // Launch home screen popup if scheduled time has arrived or passed
        if (System.currentTimeMillis() >= occurrence.scheduledEpochMillis) {
            com.doseflow.app.ui.popup.DoseReminderPopupActivity.launch(
                context = context,
                occurrenceId = occurrenceId,
                medicineName = medicine?.name ?: "Medicine",
                dosage = medicine?.dosage ?: "",
                unit = medicine?.unit ?: "",
                instructions = medicine?.instructions ?: "",
                scheduledEpochMillis = occurrence.scheduledEpochMillis
            )
        }

        // Schedule auto-missed transition after a further 30 minutes
        val now = Instant.now()
        val missedTime = now.plus(DEFAULT_MISSED_DELAY).toEpochMilli()
        alarmScheduler.scheduleMissedTransitionAlarm(missedTime, occurrenceId)
    }

    private suspend fun handleMissedAlarm(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId) ?: return
        if (occurrence.status != DoseStatus.PENDING.name) {
            Log.i(TAG, "Occurrence $occurrenceId is no longer pending (${occurrence.status}); skipping missed transition.")
            return
        }

        val nowEpoch = System.currentTimeMillis()
        val missedOccurrence = occurrence.copy(
            status = DoseStatus.MISSED.name,
            actionEpochMillis = nowEpoch,
            updatedAtEpochMillis = nowEpoch
        )
        db.doseOccurrenceDao().updateOccurrence(missedOccurrence)
        NotificationHelper.dismissNotification(context, occurrenceId)

        com.doseflow.app.data.sync.OutboxSyncEngine.enqueueEvent(
            db = db,
            aggregateType = "DOSE_OCCURRENCE",
            aggregateId = occurrenceId,
            eventType = "DOSE_MISSED",
            payloadJson = """{"occurrenceId":"$occurrenceId","status":"MISSED","timestamp":$nowEpoch}"""
        )
        com.doseflow.app.data.sync.OutboxSyncEngine.triggerSync(context)

        Log.i(TAG, "Occurrence $occurrenceId auto-transitioned to MISSED.")
        rescheduleNextChainedAlarm(db, alarmScheduler)
    }

    private suspend fun handleSnoozeAlarm(
        context: Context,
        db: AppDatabase,
        alarmScheduler: AlarmScheduler,
        occurrenceId: String
    ) {
        val occurrence = db.doseOccurrenceDao().getOccurrenceById(occurrenceId) ?: return
        if (occurrence.status != DoseStatus.SNOOZED.name) {
            Log.i(TAG, "Occurrence $occurrenceId is no longer snoozed (${occurrence.status}).")
            return
        }

        // Return to pending status and re-alert
        val nowEpoch = System.currentTimeMillis()
        val rePending = occurrence.copy(
            status = DoseStatus.PENDING.name,
            snoozedUntilEpochMillis = null,
            updatedAtEpochMillis = nowEpoch
        )
        db.doseOccurrenceDao().updateOccurrence(rePending)

        val medicine = db.medicineDao().getMedicineById(occurrence.medicineId)
        NotificationHelper.postDoseNotification(
            context = context,
            occurrenceId = occurrenceId,
            medicineName = medicine?.name ?: "Medicine",
            dosage = medicine?.dosage ?: "",
            unit = medicine?.unit ?: "",
            instructions = medicine?.instructions ?: "",
            scheduledEpochMillis = occurrence.scheduledEpochMillis,
            isEscalation = false
        )

        // Launch home screen popup if scheduled time has arrived or passed
        if (System.currentTimeMillis() >= occurrence.scheduledEpochMillis) {
            com.doseflow.app.ui.popup.DoseReminderPopupActivity.launch(
                context = context,
                occurrenceId = occurrenceId,
                medicineName = medicine?.name ?: "Medicine",
                dosage = medicine?.dosage ?: "",
                unit = medicine?.unit ?: "",
                instructions = medicine?.instructions ?: "",
                scheduledEpochMillis = occurrence.scheduledEpochMillis
            )
        }

        // Schedule escalation in 15 minutes
        val escalationTime = Instant.now().plus(DEFAULT_ESCALATION_DELAY).toEpochMilli()
        alarmScheduler.scheduleEscalationAlarm(escalationTime, occurrenceId)
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_DOSE_ALARM = "com.doseflow.app.ACTION_DOSE_ALARM"
        const val ACTION_ESCALATION_ALARM = "com.doseflow.app.ACTION_ESCALATION_ALARM"
        const val ACTION_MISSED_ALARM = "com.doseflow.app.ACTION_MISSED_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.doseflow.app.ACTION_SNOOZE_ALARM"

        const val EXTRA_OCCURRENCE_ID = "EXTRA_OCCURRENCE_ID"
        const val WAKE_LOCK_TAG = "com.doseflow.app:AlarmReceiverWakeLock"
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L

        val DEFAULT_ESCALATION_DELAY: Duration = Duration.ofMinutes(15)
        val DEFAULT_MISSED_DELAY: Duration = Duration.ofMinutes(30)
        val SNOOZE_DURATION: Duration = Duration.ofMinutes(10)

        /**
         * Executes alarm handling ensuring the wake lock is held across the entire
         * asynchronous Room SQLite transaction and notification dispatch.
         */
        suspend fun executeWithWakeLock(
            wakeLock: WakeLockController?,
            timeoutMs: Long = WAKE_LOCK_TIMEOUT_MS,
            block: suspend () -> Unit
        ) {
            wakeLock?.acquire(timeoutMs)
            try {
                block()
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }

        suspend fun rescheduleNextChainedAlarm(db: AppDatabase, alarmScheduler: AlarmScheduler) {
            val pending = db.doseOccurrenceDao().getPendingOrSnoozedOccurrences()
            val nowEpoch = System.currentTimeMillis()

            val nextOccurrence = pending
                .mapNotNull { occ ->
                    val targetTime = if (occ.status == DoseStatus.SNOOZED.name && occ.snoozedUntilEpochMillis != null) {
                        occ.snoozedUntilEpochMillis
                    } else {
                        occ.scheduledEpochMillis
                    }
                    if (targetTime >= nowEpoch) targetTime to occ.id else null
                }
                .minByOrNull { it.first }

            if (nextOccurrence != null) {
                alarmScheduler.scheduleDoseAlarm(nextOccurrence.first, nextOccurrence.second)
                Log.i(TAG, "Chained next alarm for occurrence=${nextOccurrence.second} at ${nextOccurrence.first}")
            } else {
                Log.i(TAG, "No future pending occurrences found to schedule.")
            }
        }
    }
}

interface WakeLockController {
    val isHeld: Boolean
    fun acquire(timeoutMs: Long)
    fun release()
}

class AndroidWakeLockController(private val wakeLock: android.os.PowerManager.WakeLock) : WakeLockController {
    override val isHeld: Boolean get() = wakeLock.isHeld
    override fun acquire(timeoutMs: Long) = wakeLock.acquire(timeoutMs)
    override fun release() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}

