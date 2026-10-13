package com.doseflow.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.theme.DoseFlowTheme
import com.doseflow.app.ui.components.DoseCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DoseCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun doseCard_displaysMedicineDetailsAndTriggersActions() {
        var takenClicked = false
        var snoozeClicked = false
        var skipClicked = false

        val now = Instant.now()
        val occurrence = DoseOccurrence(
            id = "occ-test-1",
            scheduleId = "sched-test-1",
            medicineId = "med-test-1",
            scheduledTime = now,
            intendedLocalDate = LocalDate.now(),
            intendedLocalTime = LocalTime.now(),
            status = DoseStatus.PENDING
        )

        composeTestRule.setContent {
            DoseFlowTheme {
                DoseCard(
                    occurrence = occurrence,
                    medicineName = "Amoxicillin",
                    dosage = "500",
                    unit = "mg",
                    instructions = "Take after meal",
                    onTakeClick = { takenClicked = true },
                    onSkipClick = { skipClicked = true },
                    onSnoozeClick = { snoozeClicked = true }
                )
            }
        }

        // Verify content rendered
        composeTestRule.onNodeWithText("Amoxicillin").assertExists()
        composeTestRule.onNodeWithText("500 mg").assertExists()
        composeTestRule.onNodeWithText("Take after meal").assertExists()
        composeTestRule.onNodeWithText("PENDING").assertExists()

        // Verify action buttons exist and respond
        composeTestRule.onNodeWithText("Taken").performClick()
        assertTrue(takenClicked)

        composeTestRule.onNodeWithText("Snooze 10m").performClick()
        assertTrue(snoozeClicked)

        composeTestRule.onNodeWithText("Skip").performClick()
        assertTrue(skipClicked)
    }
}
