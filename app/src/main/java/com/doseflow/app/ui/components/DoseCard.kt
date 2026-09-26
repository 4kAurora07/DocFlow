package com.doseflow.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doseflow.app.domain.model.DoseOccurrence
import com.doseflow.app.domain.model.DoseStatus
import com.doseflow.app.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DoseCard(
    occurrence: DoseOccurrence,
    medicineName: String,
    dosage: String,
    unit: String,
    instructions: String,
    onTakeClick: (String) -> Unit,
    onSkipClick: (String) -> Unit,
    onSnoozeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    val formattedScheduledTime = occurrence.scheduledTime.atZone(ZoneId.systemDefault()).format(timeFormatter)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val dosageText = if (dosage.isNotBlank()) "$dosage $unit" else ""
                    if (dosageText.isNotBlank()) {
                        Text(
                            text = dosageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusBadge(status = occurrence.status)
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scheduled: $formattedScheduledTime",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (instructions.isNotBlank()) {
                    Text(
                        text = instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Action section depending on status
            when (occurrence.status) {
                DoseStatus.PENDING, DoseStatus.SNOOZED -> {
                    Spacer(modifier = Modifier.height(Dimens.SpacingMedium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        Button(
                            onClick = { onTakeClick(occurrence.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Taken", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onSnoozeClick(occurrence.id) },
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Snooze 10m")
                        }

                        OutlinedButton(
                            onClick = { onSkipClick(occurrence.id) },
                            shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                        ) {
                            Text("Skip")
                        }
                    }
                }
                DoseStatus.TAKEN -> {
                    occurrence.takenTimestamp?.let { takenInstant ->
                        val takenTime = takenInstant.atZone(ZoneId.systemDefault()).format(timeFormatter)
                        Text(
                            text = "Taken at $takenTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = Dimens.SpacingSmall)
                        )
                    }
                }
                DoseStatus.SKIPPED -> {
                    occurrence.actionTimestamp?.let { skipInstant ->
                        val skipTime = skipInstant.atZone(ZoneId.systemDefault()).format(timeFormatter)
                        Text(
                            text = "Skipped at $skipTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = Dimens.SpacingSmall)
                        )
                    }
                }
                DoseStatus.MISSED -> {
                    Text(
                        text = "Missed (no action taken within 45 min)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Dimens.SpacingSmall)
                    )
                }
            }
        }
    }
}
