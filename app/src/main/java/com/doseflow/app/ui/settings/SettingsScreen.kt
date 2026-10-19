package com.doseflow.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.doseflow.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var expandedOem by remember { mutableStateOf<String?>(null) }

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
                    Text(
                        text = "Settings & Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Dimens.SpacingMedium,
                end = Dimens.SpacingMedium,
                top = Dimens.SpacingSmall,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            // Caregiver Remote Monitoring & Code Sharing
            item {
                Text(
                    text = "Caregiver Remote Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Share a code to let family or caregivers monitor adherence in real-time on the web.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Caregiver Web Access",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.caregiverCode != null) {
                                Text(
                                    text = "Active Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))



                        if (uiState.caregiverCode != null) {
                            Surface(
                                shape = RoundedCornerShape(Dimens.CardCornerRadius),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimens.SpacingMedium),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "PAIRING CODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = uiState.caregiverCode ?: "",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val clip = android.content.ClipData.newPlainText("Caregiver Code", uiState.caregiverCode)
                                                val mgr = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                mgr?.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Code copied: ${uiState.caregiverCode}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("Copy")
                                        }
                                        Button(
                                            onClick = {
                                                val code = uiState.caregiverCode ?: ""
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "Here is my DoseFlow Caregiver Access Code: $code. Enter this code on the DoseFlow Caregiver Portal to view my medications in real-time."
                                                    )
                                                }
                                                context.startActivity(android.content.Intent.createChooser(intent, "Share Caregiver Code"))
                                            }
                                        ) {
                                            Text("Share")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { viewModel.generateCaregiverCode() },
                                    enabled = !uiState.isSyncing
                                ) {
                                    Text("Regenerate Code")
                                }
                                TextButton(
                                    onClick = { viewModel.syncDataNow() },
                                    enabled = !uiState.isSyncing
                                ) {
                                    Text(if (uiState.isSyncing) "Syncing..." else "Sync Real Data Now")
                                }
                            }
                        } else {
                            Text(
                                text = "Generate a secure pairing code to allow family or caregivers to view your daily doses in real-time on the website.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                            Button(
                                onClick = { viewModel.generateCaregiverCode() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSyncing
                            ) {
                                Text(if (uiState.isSyncing) "Connecting..." else "Generate & Share Caregiver Code")
                            }
                        }

                        if (uiState.syncStatusMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.syncStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.connectionSuccess == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Dose Reminder Preferences
            item {
                Text(
                    text = "Dose Reminder Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SpacingMedium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Dimens.SpacingMedium)
                        ) {
                            Text(
                                text = "Home-Screen Pop-up Alert",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.homeScreenPopupEnabled)
                                    "When a dose time has passed and you are on your home screen (or unlock your phone), a floating popup appears in front of the screen. (Never shown on the lock screen to protect your privacy)."
                                else
                                    "Disabled: Reminders will only appear in your notifications bar without popping up over the home screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.homeScreenPopupEnabled,
                            onCheckedChange = { viewModel.setHomeScreenPopupEnabled(it) }
                        )
                    }
                }
            }

            // System Alarm Permissions
            item {
                Text(
                    text = "System Alarm Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                PermissionStatusCard(
                    title = "Exact Alarms (SCHEDULE_EXACT_ALARM)",
                    description = "Enables alarms to fire at exact second intervals even while the phone is locked.",
                    isGranted = uiState.hasExactAlarmPermission,
                    actionLabel = "Open Alarm Settings",
                    onActionClick = { PowerAndPermissionHelper.openExactAlarmSettings(context) }
                )
            }

            item {
                PermissionStatusCard(
                    title = "Post Notifications (POST_NOTIFICATIONS)",
                    description = "Allows full-screen and sound notifications with Taken, Snooze, and Skip actions.",
                    isGranted = uiState.hasNotificationPermission,
                    actionLabel = "Open Notification Settings",
                    onActionClick = { PowerAndPermissionHelper.openNotificationSettings(context) }
                )
            }

            item {
                PermissionStatusCard(
                    title = "Battery Optimization Exemption",
                    description = "Prevents Android Doze from putting reminder services into deep sleep.",
                    isGranted = uiState.isIgnoringBatteryOptimizations,
                    actionLabel = "Request Exemption",
                    onActionClick = { PowerAndPermissionHelper.requestIgnoreBatteryOptimizations(context) }
                )
            }

            item {
                PermissionStatusCard(
                    title = "Display Over Other Apps (Overlay)",
                    description = if (!uiState.canDrawOverlays) {
                        "Allows the medication reminder popup to appear in front of your home screen even when the app is completely closed.\n\n💡 Android 13+ Tip: If the switch is greyed out with 'Restricted setting', go to Settings -> Apps -> DoseFlow -> tap the (⋮) menu in top-right corner -> tap 'Allow restricted settings'."
                    } else {
                        "Allows the medication reminder popup to appear in front of your home screen even when the app is completely closed or not running in the background."
                    },
                    isGranted = uiState.canDrawOverlays,
                    actionLabel = "Allow 'Display over other apps'",
                    onActionClick = { PowerAndPermissionHelper.openOverlaySettings(context) }
                )
            }



            // OEM Aggressive Memory Management
            item {
                Text(
                    text = "OEM Background Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Dimens.SpacingSmall)
                )
                Text(
                    text = "Certain manufacturers aggressively kill background apps. Follow the steps below for your brand to ensure reliable delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            val oemList = listOf(
                "Samsung (One UI)" to "1. Open Settings -> Battery and device care -> Background usage limits.\n2. Add DoseFlow to 'Never sleeping apps'.\n3. Under Lock screen settings, ensure Notifications are turned On.",
                "Xiaomi / Redmi / Poco (MIUI / HyperOS)" to "1. Open Settings -> Apps -> Manage Apps -> DoseFlow.\n2. Turn ON 'Autostart'.\n3. Under Battery Saver, select 'No restrictions'.",
                "OnePlus / Oppo / Realme (OxygenOS / ColorOS)" to "1. Open Settings -> Battery -> App battery management -> DoseFlow.\n2. Turn ON 'Allow background activity' and 'Allow auto-launch'.",
                "Huawei / Honor (EMUI / MagicOS)" to "1. Open Settings -> Battery -> App launch.\n2. Find DoseFlow, switch to 'Manage manually'.\n3. Enable Auto-launch, Secondary launch, and Run in background."
            )

            items(oemList.size) { idx ->
                val (oemName, instructions) = oemList[idx]
                val isExpanded = expandedOem == oemName
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedOem = if (isExpanded) null else oemName
                        },
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = oemName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isExpanded) "Hide" else "Instructions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = Dimens.SpacingSmall)) {
                                Text(
                                    text = instructions,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Privacy & Cloud Sync Architecture
            item {
                Text(
                    text = "Privacy & Cloud Sync Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Dimens.SpacingSmall)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                                Text(
                                    text = "Real-Time Cloud Synchronization",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(Dimens.BadgeCornerRadius)
                            ) {
                                Text(
                                    text = "Online Cloud Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                        Text(
                            text = "DoseFlow connects online over 5G/4G cellular networks and Wi-Fi to synchronize your medication schedules, taken/skipped logs, and daily adherence directly with the secure cloud server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                            Text(
                                text = "Global Caregiver Monitoring",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                        Text(
                            text = "Family members and caregivers can view live updates from anywhere in the world on the web dashboard (adhivio-med.onrender.com) using your unique 6-character pairing code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                            Text(
                                text = "Encrypted & Read-Only Protection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                        Text(
                            text = "All data transmitted to the cloud is encrypted in-transit via TLS 1.3 HTTPS. The caregiver dashboard is strictly read-only — remote users cannot alter your dosages or cancel your alarms. You can regenerate your code at any time to instantly revoke access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

                        Surface(
                            shape = RoundedCornerShape(Dimens.BadgeCornerRadius),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                                Text(
                                    text = "PRODUCTION CLOUD ARCHITECTURE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Cloud Server: https://adhivio-med.onrender.com",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "• Connection Mode: Online Real-Time Sync (Cellular / Wi-Fi)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "• Security: TLS 1.3 HTTPS Encryption",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "• Access Control: Authenticated 6-Character Pairing Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "• Alarm Reliability: Native Android AlarmManager (Exact & While-Idle)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "• Device: ${uiState.manufacturer.uppercase()} ${uiState.model} • Android ${uiState.androidVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "• Environment: Production Release v1.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = if (isGranted) "Granted" else "Action Required",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(Dimens.SpacingMedium))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
