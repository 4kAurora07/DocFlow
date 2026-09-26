package com.doseflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doseflow.app.platform.util.AppPermissionStatus
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import com.doseflow.app.theme.*

@Composable
fun PermissionWarningBanner(
    status: AppPermissionStatus,
    modifier: Modifier = Modifier
) {
    if (status.isFullyConfigured) return

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) WarningAlertContainerDark else WarningAlertContainerLight
    val contentColor = if (isDark) WarningAlertDark else WarningAlertLight

    val (title, description, buttonLabel, onClick) = when {
        status.hasExactAlarmIssue -> {
            Quadruple(
                "Exact Alarm Permission Required",
                "Android has disabled exact alarms for DoseFlow. Your reminders cannot be guaranteed to fire on time until this permission is granted in system settings.",
                "Enable Exact Alarms"
            ) {
                context.startActivity(PowerAndPermissionHelper.createExactAlarmSettingsIntent(context))
            }
        }
        status.hasNotificationIssue -> {
            Quadruple(
                "Notifications Disabled",
                "Notifications are blocked for DoseFlow. You will not receive alerts when medication is due.",
                "Enable Notifications"
            ) {
                context.startActivity(PowerAndPermissionHelper.createAppDetailsSettingsIntent(context))
            }
        }
        else -> return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(Dimens.CardCornerRadius))
            .padding(Dimens.SpacingMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Dimens.SpacingSmall, bottom = Dimens.SpacingMedium)
        )

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = contentColor,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
        ) {
            Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PermissionWarningBanner(
    title: String,
    description: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) WarningAlertContainerDark else WarningAlertContainerLight
    val contentColor = if (isDark) WarningAlertDark else WarningAlertLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(Dimens.CardCornerRadius))
            .padding(Dimens.SpacingMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Dimens.SpacingSmall, bottom = Dimens.SpacingMedium)
        )

        Button(
            onClick = onActionClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = contentColor,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
        ) {
            Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
