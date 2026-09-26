package com.doseflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.theme.*

@Composable
fun StatusBadge(
    status: DoseStatus,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val (bgColor, textColor, label) = when (status) {
        DoseStatus.TAKEN -> Triple(
            if (isDark) StatusTakenContainerDark else StatusTakenContainerLight,
            if (isDark) StatusTakenDark else StatusTakenLight,
            "Taken"
        )
        DoseStatus.PENDING -> Triple(
            if (isDark) StatusUpcomingContainerDark else StatusUpcomingContainerLight,
            if (isDark) StatusUpcomingDark else StatusUpcomingLight,
            "Due"
        )
        DoseStatus.SKIPPED -> Triple(
            if (isDark) StatusSkippedContainerDark else StatusSkippedContainerLight,
            if (isDark) StatusSkippedDark else StatusSkippedLight,
            "Skipped"
        )
        DoseStatus.MISSED -> Triple(
            if (isDark) StatusMissedContainerDark else StatusMissedContainerLight,
            if (isDark) StatusMissedDark else StatusMissedLight,
            "Missed"
        )
        DoseStatus.SNOOZED -> Triple(
            if (isDark) StatusSnoozedContainerDark else StatusSnoozedContainerLight,
            if (isDark) StatusSnoozedDark else StatusSnoozedLight,
            "Snoozed"
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(Dimens.BadgeCornerRadius))
            .padding(horizontal = Dimens.SpacingSmall, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
