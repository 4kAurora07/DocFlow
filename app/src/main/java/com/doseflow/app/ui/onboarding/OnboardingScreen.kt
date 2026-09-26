package com.doseflow.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import com.doseflow.app.theme.Dimens

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onAddFirstMedicine: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.SpacingLarge),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = Dimens.SpacingMedium)
            ) {
                for (i in 0..2) {
                    val isActive = i == uiState.currentStep
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Step Content
            when (uiState.currentStep) {
                0 -> WelcomeStepContent(modifier = Modifier.weight(1f))
                1 -> PermissionsStepContent(
                    hasExactAlarm = uiState.hasExactAlarmPermission,
                    hasNotifications = uiState.hasNotificationPermission,
                    onGrantExactAlarm = { PowerAndPermissionHelper.openExactAlarmSettings(context) },
                    onGrantNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PowerAndPermissionHelper.openNotificationSettings(context)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                2 -> ReadyStepContent(modifier = Modifier.weight(1f))
            }

            // Bottom action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                when (uiState.currentStep) {
                    0 -> {
                        Button(
                            onClick = { viewModel.nextStep() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Get Started", fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Button(
                            onClick = { viewModel.nextStep() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Next Step", fontWeight = FontWeight.Bold)
                        }
                    }
                    2 -> {
                        Button(
                            onClick = {
                                viewModel.completeOnboarding()
                                onAddFirstMedicine()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Add First Medication", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.completeOnboarding()
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Go to Today Timeline")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DoseFlow",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
        Text(
            text = "Reliable, offline-first medication schedules that respect your privacy.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

        FeatureHighlight(
            title = "Exact Alarm Engine",
            description = "Never miss a scheduled dose even when device is locked in Doze mode."
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))
        FeatureHighlight(
            title = "100% Local SQLite Storage",
            description = "Your health data never leaves your device. No cloud sync, no tracking."
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))
        FeatureHighlight(
            title = "Adherence & Snooze Controls",
            description = "Log doses as taken, snoozed, or skipped with one-tap lock screen actions."
        )
    }
}

@Composable
private fun PermissionsStepContent(
    hasExactAlarm: Boolean,
    hasNotifications: Boolean,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permission Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
        Text(
            text = "To guarantee medication reminders wake your phone on time, Android requires these two permissions:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

        PermissionCard(
            title = "Exact Alarms",
            description = "Allows DoseFlow to schedule precise alarms that wake the device.",
            isGranted = hasExactAlarm,
            onGrant = onGrantExactAlarm
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

        PermissionCard(
            title = "Notifications",
            description = "Allows full notification alerts with Taken, Snooze, and Skip actions.",
            isGranted = hasNotifications,
            onGrant = onGrantNotifications
        )
    }
}

@Composable
private fun ReadyStepContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))
        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
        Text(
            text = "DoseFlow is ready. You can now add your first medication and configure daily or interval reminder schedules.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureHighlight(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onGrant,
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                ) {
                    Text("Grant")
                }
            }
        }
    }
}
