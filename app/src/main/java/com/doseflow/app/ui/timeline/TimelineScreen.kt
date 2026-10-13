package com.doseflow.app.ui.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.doseflow.app.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doseflow.app.platform.util.PowerAndPermissionHelper
import com.doseflow.app.theme.Dimens
import com.doseflow.app.ui.components.DoseCard
import com.doseflow.app.ui.components.EmptyStateView
import com.doseflow.app.ui.components.PermissionWarningBanner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onNavigateToAddMedicine: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "DoseFlow Logo",
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                },
                title = {
                    Column {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = todayFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddMedicine,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Medicine")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission warning banners (re-checked on ON_RESUME, non-dismissible while disabled)
            if (!uiState.hasExactAlarmPermission) {
                PermissionWarningBanner(
                    title = "Exact Alarms Disabled",
                    description = "Android requires exact alarm permission for reliable medication reminders.",
                    actionLabel = "Grant Permission",
                    onActionClick = { PowerAndPermissionHelper.openExactAlarmSettings(context) },
                    modifier = Modifier.padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall)
                )
            }

            if (!uiState.hasNotificationPermission) {
                PermissionWarningBanner(
                    title = "Notifications Disabled",
                    description = "Allow DoseFlow to post notifications so you never miss a dose.",
                    actionLabel = "Enable Notifications",
                    onActionClick = { PowerAndPermissionHelper.openNotificationSettings(context) },
                    modifier = Modifier.padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall)
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.doses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "All Caught Up!",
                        description = "No medication doses are scheduled for today.",
                        actionLabel = "Add Medicine",
                        onActionClick = onNavigateToAddMedicine
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.SpacingMedium,
                        end = Dimens.SpacingMedium,
                        top = Dimens.SpacingSmall,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                ) {
                    items(
                        items = uiState.doses,
                        key = { it.occurrence.id }
                    ) { item ->
                        DoseCard(
                            occurrence = item.occurrence,
                            medicineName = item.medicine.name,
                            dosage = item.medicine.dosage,
                            unit = item.medicine.unit,
                            instructions = item.medicine.instructions,
                            onTakeClick = { id -> viewModel.markTaken(id) },
                            onSkipClick = { id -> viewModel.markSkipped(id) },
                            onSnoozeClick = { id -> viewModel.snooze(id) }
                        )
                    }
                }
            }
        }
    }
}
