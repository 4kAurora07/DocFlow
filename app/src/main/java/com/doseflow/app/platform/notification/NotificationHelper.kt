package com.doseflow.app.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.graphics.BitmapFactory
import com.doseflow.app.MainActivity
import com.doseflow.app.R
import com.doseflow.app.platform.receiver.NotificationActionReceiver

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_DOSES_HIGH = "doseflow_doses_high"
    const val CHANNEL_INFO_LOW = "doseflow_info_low"

    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // High-importance channel for dose alarms
        val doseSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val doseChannel = NotificationChannel(
            CHANNEL_DOSES_HIGH,
            "Medicine Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority alarms and reminders when doses are due."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(doseSoundUri, audioAttributes)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // Low-importance channel for sync/background status
        val infoChannel = NotificationChannel(
            CHANNEL_INFO_LOW,
            "App Status & Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Informational status and background sync updates."
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(listOf(doseChannel, infoChannel))
        Log.i(TAG, "Notification channels created/verified.")
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postDoseNotification(
        context: Context,
        occurrenceId: String,
        medicineName: String,
        dosage: String,
        unit: String,
        instructions: String,
        scheduledEpochMillis: Long,
        isEscalation: Boolean = false
    ): Boolean {
        createNotificationChannels(context)

        if (!areNotificationsEnabled(context)) {
            Log.w(TAG, "Notification permission is not granted; unable to post dose notification for $occurrenceId")
            return false
        }

        val notificationId = occurrenceId.hashCode()

        // Content intent: Tapping notification opens the high-contrast Dose Reminder Popup directly
        val contentIntent = Intent(context, com.doseflow.app.ui.popup.DoseReminderPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_DOSAGE, dosage)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_UNIT, unit)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_INSTRUCTIONS, instructions)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_SCHEDULED_TIME, scheduledEpochMillis)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Taken
        val takenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TAKEN
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Skip
        val skipIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SKIP
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10m
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isEscalation) {
            "Reminder: Due Dose for $medicineName"
        } else {
            "Time for your medicine: $medicineName"
        }

        val detailText = buildString {
            if (dosage.isNotBlank()) append("$dosage $unit")
            if (instructions.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(instructions)
            }
        }

        val appLogoBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
        } catch (_: Exception) {
            null
        }

        // Full screen intent to popup over phone home screen / lock screen if time has passed
        val fullScreenIntent = Intent(context, com.doseflow.app.ui.popup.DoseReminderPopupActivity::class.java).apply {
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_DOSAGE, dosage)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_UNIT, unit)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_INSTRUCTIONS, instructions)
            putExtra(com.doseflow.app.ui.popup.DoseReminderPopupActivity.EXTRA_SCHEDULED_TIME, scheduledEpochMillis)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 4,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOSES_HIGH)
            .setSmallIcon(R.drawable.ic_stat_doseflow)
            .apply {
                if (appLogoBitmap != null) {
                    setLargeIcon(appLogoBitmap)
                }
                val isPopupEnabled = try {
                    com.doseflow.app.data.DoseFlowRepository(context).isHomeScreenPopupEnabled()
                } catch (_: Exception) {
                    true
                }
                if (isPopupEnabled && System.currentTimeMillis() >= scheduledEpochMillis) {
                    setFullScreenIntent(fullScreenPendingIntent, true)
                }
            }
            .setColor(0xFF2B5B84.toInt())
            .setContentTitle(title)
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (detailText.isNotBlank()) "$detailText\n\nTap '✓ I TOOK IT' below once consumed." else "Time to take your scheduled dose. Tap below when taken."
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stat_doseflow,
                    "✓ I TOOK IT",
                    takenPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_sync,
                    "⏰ SNOOZE 10M",
                    snoozePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "✕ SKIP DOSE",
                    skipPendingIntent
                ).build()
            )
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.i(TAG, "Posted dose notification for $occurrenceId (isEscalation=$isEscalation)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException posting notification for $occurrenceId: ${e.message}", e)
            false
        }
    }

    fun dismissNotification(context: Context, occurrenceId: String) {
        val notificationId = occurrenceId.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)
        Log.i(TAG, "Dismissed notification for $occurrenceId")
    }
}
