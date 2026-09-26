package com.doseflow.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.doseflow.app.platform.util.AppPermissionStatus
import com.doseflow.app.theme.DoseFlowTheme
import com.doseflow.app.ui.components.PermissionWarningBanner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PermissionWarningBannerComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun banner_rendersWhenExactAlarmMissing() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = false,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        composeTestRule.setContent {
            DoseFlowTheme {
                PermissionWarningBanner(status = status)
            }
        }

        composeTestRule.onNodeWithText("Exact Alarm Permission Required").assertExists()
        composeTestRule.onNodeWithText("Enable Exact Alarms").assertExists()
    }

    @Test
    fun banner_rendersWhenNotificationsMissing() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = true,
            areNotificationsEnabled = false,
            isIgnoringBatteryOptimizations = true
        )

        composeTestRule.setContent {
            DoseFlowTheme {
                PermissionWarningBanner(status = status)
            }
        }

        composeTestRule.onNodeWithText("Notifications Disabled").assertExists()
        composeTestRule.onNodeWithText("Enable Notifications").assertExists()
    }

    @Test
    fun banner_doesNotRenderWhenFullyConfigured() {
        val status = AppPermissionStatus(
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true,
            isIgnoringBatteryOptimizations = true
        )

        composeTestRule.setContent {
            DoseFlowTheme {
                PermissionWarningBanner(status = status)
            }
        }

        composeTestRule.onNodeWithText("Exact Alarm Permission Required").assertDoesNotExist()
        composeTestRule.onNodeWithText("Notifications Disabled").assertDoesNotExist()
    }

    @Test
    fun banner_customOverload_triggersAction() {
        var actionClicked = false

        composeTestRule.setContent {
            DoseFlowTheme {
                PermissionWarningBanner(
                    title = "Custom Warning",
                    description = "Custom Description",
                    actionLabel = "Fix Now",
                    onActionClick = { actionClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Custom Warning").assertExists()
        composeTestRule.onNodeWithText("Custom Description").assertExists()
        composeTestRule.onNodeWithText("Fix Now").performClick()
        assertTrue(actionClicked)
    }
}
