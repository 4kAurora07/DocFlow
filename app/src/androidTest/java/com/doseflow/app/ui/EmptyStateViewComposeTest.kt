package com.doseflow.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.doseflow.app.theme.DoseFlowTheme
import com.doseflow.app.ui.components.EmptyStateView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmptyStateViewComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyState_displaysTitleAndTriggersAction() {
        var actionClicked = false

        composeTestRule.setContent {
            DoseFlowTheme {
                EmptyStateView(
                    title = "All Caught Up!",
                    description = "No medication doses are scheduled for today.",
                    actionLabel = "Add Medicine",
                    onActionClick = { actionClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("All Caught Up!").assertExists()
        composeTestRule.onNodeWithText("No medication doses are scheduled for today.").assertExists()
        composeTestRule.onNodeWithText("Add Medicine").performClick()
        assertTrue(actionClicked)
    }
}
