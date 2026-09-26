package com.doseflow.app.ui.components

import com.doseflow.app.platform.util.AppPermissionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionBannerBehaviorTest {

    @Test
    fun fullyConfigured_hasNoIssues() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        assertTrue(status.isFullyConfigured)
        assertFalse(status.hasExactAlarmIssue)
        assertFalse(status.hasNotificationIssue)
    }

    @Test
    fun missingExactAlarm_flagsIssueAndBlocksFullConfiguration() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = false,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        assertFalse(status.isFullyConfigured)
        assertTrue(status.hasExactAlarmIssue)
        assertFalse(status.hasNotificationIssue)
    }

    @Test
    fun missingNotifications_flagsIssueAndBlocksFullConfiguration() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = true,
            areNotificationsEnabled = false,
            isIgnoringBatteryOptimizations = true
        )

        assertFalse(status.isFullyConfigured)
        assertFalse(status.hasExactAlarmIssue)
        assertTrue(status.hasNotificationIssue)
    }

    @Test
    fun bothMissing_flagsBothIssues() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = false,
            areNotificationsEnabled = false,
            isIgnoringBatteryOptimizations = false
        )

        assertFalse(status.isFullyConfigured)
        assertTrue(status.hasExactAlarmIssue)
        assertTrue(status.hasNotificationIssue)
    }
}
