package com.doseflow.app.platform.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class OemAdvice(
    val manufacturer: String,
    val title: String,
    val description: String,
    val steps: List<String>
)

data class AppPermissionStatus(
    val canScheduleExactAlarms: Boolean,
    val areNotificationsEnabled: Boolean,
    val isIgnoringBatteryOptimizations: Boolean
) {
    val hasExactAlarmIssue: Boolean get() = !canScheduleExactAlarms
    val hasNotificationIssue: Boolean get() = !areNotificationsEnabled
    val isFullyConfigured: Boolean get() = canScheduleExactAlarms && areNotificationsEnabled
}

object PowerAndPermissionHelper {

    fun checkCurrentPermissions(context: Context): AppPermissionStatus = AppPermissionStatus(
        canScheduleExactAlarms = canScheduleExactAlarms(context),
        areNotificationsEnabled = areNotificationsEnabled(context),
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
    )

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun createExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun createRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun openExactAlarmSettings(context: Context) {
        context.startActivity(createExactAlarmSettingsIntent(context))
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
        context.startActivity(intent)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        context.startActivity(createRequestIgnoreBatteryOptimizationsIntent(context))
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun createOverlaySettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(createOverlaySettingsIntent(context))
    }

    fun hasNotificationPermission(context: Context): Boolean = areNotificationsEnabled(context)

    fun getOemTroubleshootingAdvice(): List<OemAdvice> = listOf(
        OemAdvice(
            manufacturer = "Samsung",
            title = "Samsung Sleeping Apps",
            description = "One UI may place apps in 'Sleeping' or 'Deep sleeping' lists if not opened frequently, delaying exact alarms.",
            steps = listOf(
                "Open Settings -> Battery and device care -> Battery.",
                "Tap 'Background usage limits'.",
                "Ensure DoseFlow is listed under 'Never sleeping apps'."
            )
        ),
        OemAdvice(
            manufacturer = "Xiaomi / Redmi / Poco",
            title = "MIUI / HyperOS Autostart & Battery Saver",
            description = "Xiaomi devices kill background processes unless Autostart is allowed and battery saver is set to 'No restrictions'.",
            steps = listOf(
                "Open Settings -> Apps -> Manage Apps -> DoseFlow.",
                "Enable 'Autostart'.",
                "Under 'Battery saver', choose 'No restrictions'."
            )
        ),
        OemAdvice(
            manufacturer = "OnePlus / Oppo / Realme",
            title = "OxygenOS / ColorOS App Battery Management",
            description = "Aggressive power management can pause background alarms when the screen is turned off for extended periods.",
            steps = listOf(
                "Open Settings -> Battery -> More settings -> App battery management.",
                "Select DoseFlow and allow 'Allow background activity' and 'Allow auto-launch'."
            )
        ),
        OemAdvice(
            manufacturer = "Huawei / Honor",
            title = "EMUI / MagicOS PowerGenie",
            description = "Huawei's PowerGenie service aggressively terminates background apps upon screen lock.",
            steps = listOf(
                "Open Settings -> Battery -> App launch.",
                "Find DoseFlow, disable 'Manage automatically', and enable 'Auto-launch', 'Secondary launch', and 'Run in background'."
            )
        )
    )
}
