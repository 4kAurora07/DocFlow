package com.doseflow.app.ui.popup

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doseflow.app.data.DoseFlowRepository
import com.doseflow.app.platform.notification.NotificationHelper
import com.doseflow.app.theme.DoseFlowTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * System-level Activity that pops up directly over the phone's Home Screen or Lock Screen
 * when medication time has arrived or passed, ensuring seniors never forget to take their medicine.
 */
class DoseReminderPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure system to turn screen on and show over lockscreen / home screen
        setupScreenWakeAndLockFlags()

        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: run {
            finish()
            return
        }
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "Medicine"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE) ?: ""
        val unit = intent.getStringExtra(EXTRA_UNIT) ?: ""
        val instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS) ?: ""
        val scheduledEpochMillis = intent.getLongExtra(EXTRA_SCHEDULED_TIME, 0L)

        // Strict validation: Only show if the scheduled time has arrived or passed!
        val now = System.currentTimeMillis()
        if (scheduledEpochMillis > 0 && scheduledEpochMillis > now) {
            Log.w(TAG, "Scheduled time ($scheduledEpochMillis) has not arrived yet (now=$now). Skipping popup.")
            finish()
            return
        }

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
        val scheduledTimeStr = if (scheduledEpochMillis > 0) {
            try {
                timeFormatter.format(Instant.ofEpochMilli(scheduledEpochMillis))
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }

        val repository = DoseFlowRepository(applicationContext)

        // Check if disabled in user settings
        if (!repository.isHomeScreenPopupEnabled()) {
            Log.i(TAG, "Home screen popup is disabled in user settings. Skipping.")
            finish()
            return
        }


        setContent {
            DoseFlowTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HomeScreenPopupCard(
                        medicineName = medicineName,
                        dosage = dosage,
                        unit = unit,
                        instructions = instructions,
                        scheduledTimeText = scheduledTimeStr,
                        onTakeClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.markTaken(occurrenceId)
                                NotificationHelper.dismissNotification(applicationContext, occurrenceId)
                            }
                            Toast.makeText(applicationContext, "✓ Marked as Taken", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onSnoozeClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.snoozeDose(occurrenceId)
                                NotificationHelper.dismissNotification(applicationContext, occurrenceId)
                            }
                            Toast.makeText(applicationContext, "⏰ Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onSkipClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.markSkipped(occurrenceId)
                                NotificationHelper.dismissNotification(applicationContext, occurrenceId)
                            }
                            Toast.makeText(applicationContext, "Dose skipped", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun setupScreenWakeAndLockFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        private const val TAG = "DoseReminderPopup"

        const val EXTRA_OCCURRENCE_ID = "EXTRA_OCCURRENCE_ID"
        const val EXTRA_MEDICINE_NAME = "EXTRA_MEDICINE_NAME"
        const val EXTRA_DOSAGE = "EXTRA_DOSAGE"
        const val EXTRA_UNIT = "EXTRA_UNIT"
        const val EXTRA_INSTRUCTIONS = "EXTRA_INSTRUCTIONS"
        const val EXTRA_SCHEDULED_TIME = "EXTRA_SCHEDULED_TIME"

        fun launch(
            context: Context,
            occurrenceId: String,
            medicineName: String,
            dosage: String,
            unit: String,
            instructions: String,
            scheduledEpochMillis: Long
        ) {
            // Strict check: Only launch if the scheduled time has arrived or passed!
            val now = System.currentTimeMillis()
            if (scheduledEpochMillis > now) {
                Log.d(TAG, "Scheduled time is in future; not showing popup.")
                return
            }

            val intent = Intent(context, DoseReminderPopupActivity::class.java).apply {
                putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_UNIT, unit)
                putExtra(EXTRA_INSTRUCTIONS, instructions)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledEpochMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not launch DoseReminderPopupActivity: ${e.message}", e)
            }
        }
    }
}

@Composable
private fun HomeScreenPopupCard(
    medicineName: String,
    dosage: String,
    unit: String,
    instructions: String,
    scheduledTimeText: String,
    onTakeClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    onSkipClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        shadowElevation = 24.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Friendly Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Dose Reminder",
                    tint = Color(0xFF15803D),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Time For Your Medicine!",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your scheduled dose time has arrived:",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Medicine Highlight Box
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )

                    if (dosage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$dosage $unit".trim(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (instructions.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = instructions,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (scheduledTimeText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "⏰ Scheduled: $scheduledTimeText • Time Passed",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GIANT "✓ I TOOK THIS MEDICINE" BUTTON
            Button(
                onClick = onTakeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Taken",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "✓ I TOOK THIS MEDICINE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row (Snooze & Skip)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSnoozeClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Snooze",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Snooze 10m",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onSkipClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Skip Dose",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "Dismiss for now",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
