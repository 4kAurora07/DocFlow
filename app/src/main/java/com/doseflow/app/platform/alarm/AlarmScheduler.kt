package com.doseflow.app.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.doseflow.app.platform.receiver.AlarmReceiver

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun scheduleDoseAlarm(triggerEpochMillis: Long, occurrenceId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DOSE_ALARM
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val requestCode = getDoseRequestCode(occurrenceId)
        scheduleOneShot(triggerEpochMillis, requestCode, intent, "DoseAlarm($occurrenceId)")
    }

    fun scheduleEscalationAlarm(triggerEpochMillis: Long, occurrenceId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ESCALATION_ALARM
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val requestCode = getEscalationRequestCode(occurrenceId)
        scheduleOneShot(triggerEpochMillis, requestCode, intent, "EscalationAlarm($occurrenceId)")
    }

    fun scheduleMissedTransitionAlarm(triggerEpochMillis: Long, occurrenceId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MISSED_ALARM
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val requestCode = getMissedRequestCode(occurrenceId)
        scheduleOneShot(triggerEpochMillis, requestCode, intent, "MissedAlarm($occurrenceId)")
    }

    fun scheduleSnoozeAlarm(triggerEpochMillis: Long, occurrenceId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val requestCode = getSnoozeRequestCode(occurrenceId)
        scheduleOneShot(triggerEpochMillis, requestCode, intent, "SnoozeAlarm($occurrenceId)")
    }

    fun cancelDoseAlarms(occurrenceId: String) {
        cancelAlarm(getDoseRequestCode(occurrenceId), AlarmReceiver.ACTION_DOSE_ALARM)
        cancelAlarm(getEscalationRequestCode(occurrenceId), AlarmReceiver.ACTION_ESCALATION_ALARM)
        cancelAlarm(getMissedRequestCode(occurrenceId), AlarmReceiver.ACTION_MISSED_ALARM)
        cancelAlarm(getSnoozeRequestCode(occurrenceId), AlarmReceiver.ACTION_SNOOZE_ALARM)
    }

    private fun scheduleOneShot(
        triggerEpochMillis: Long,
        requestCode: Int,
        intent: Intent,
        tag: String
    ) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service unavailable; cannot schedule $tag")
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled exact alarm for $tag at epoch $triggerEpochMillis")
            } else {
                Log.w(TAG, "Exact alarms permission not granted; falling back to setAndAllowWhileIdle for $tag")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for $tag: ${e.message}", e)
        }
    }

    private fun cancelAlarm(requestCode: Int, action: String) {
        if (alarmManager == null) return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled alarm for action=$action, requestCode=$requestCode")
        }
    }

    companion object {
        private const val TAG = "AlarmScheduler"

        fun getDoseRequestCode(occurrenceId: String): Int = occurrenceId.hashCode()
        fun getEscalationRequestCode(occurrenceId: String): Int = occurrenceId.hashCode() * 31 + 1
        fun getMissedRequestCode(occurrenceId: String): Int = occurrenceId.hashCode() * 31 + 2
        fun getSnoozeRequestCode(occurrenceId: String): Int = occurrenceId.hashCode() * 31 + 3
    }
}
