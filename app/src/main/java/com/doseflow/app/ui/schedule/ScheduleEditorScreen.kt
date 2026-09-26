package com.doseflow.app.ui.schedule

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doseflow.app.theme.Dimens
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorScreen(
    medicineId: String,
    onNavigateBack: () -> Unit,
    onScheduleSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(medicineId) {
        viewModel.loadSchedule(medicineId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onScheduleSaved()
        }
    }

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    fun showSystemTimePicker(initialTime: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onTimeSelected(LocalTime.of(hourOfDay, minute))
            },
            initialTime.hour,
            initialTime.minute,
            false
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule Reminders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.SpacingMedium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            // Medication header
            uiState.medicine?.let { med ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                        Text(
                            text = med.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (med.dosage.isNotBlank()) {
                            Text(
                                text = "${med.dosage} ${med.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Frequency Type
            Text(
                text = "Frequency",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                ScheduleFrequencyType.values().forEach { freq ->
                    FilterChip(
                        selected = uiState.frequencyType == freq,
                        onClick = { viewModel.setFrequencyType(freq) },
                        label = { Text(freq.label) }
                    )
                }
            }

            // Days of week selector if DAYS_OF_WEEK
            if (uiState.frequencyType == ScheduleFrequencyType.DAYS_OF_WEEK) {
                Text(
                    text = "Select Days",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val isSelected = uiState.selectedDays.contains(day)
                        val shortName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleDay(day) },
                            label = { Text(shortName) }
                        )
                    }
                }
            }

            // Interval selector if EVERY_N_DAYS
            if (uiState.frequencyType == ScheduleFrequencyType.EVERY_N_DAYS) {
                Text(
                    text = "Repeat Interval",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                ) {
                    listOf(2, 3, 4, 7).forEach { days ->
                        FilterChip(
                            selected = uiState.intervalDays == days,
                            onClick = { viewModel.setIntervalDays(days) },
                            label = { Text("Every $days days") }
                        )
                    }
                }
            }

            // Times selector (not needed for As Needed)
            if (uiState.frequencyType != ScheduleFrequencyType.AS_NEEDED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reminder Times",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            showSystemTimePicker(LocalTime.now()) { newTime ->
                                viewModel.addTime(newTime)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Time")
                    }
                }

                // List of times
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    uiState.times.forEachIndexed { index, time ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimens.CardCornerRadius),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = time.format(timeFormatter),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            showSystemTimePicker(time) { newTime ->
                                                viewModel.updateTime(index, newTime)
                                            }
                                        }
                                    ) {
                                        Text("Change")
                                    }
                                    if (uiState.times.size > 1) {
                                        IconButton(onClick = { viewModel.removeTime(index) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Time",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick preset row
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    val presets = listOf(
                        "8:00 AM" to LocalTime.of(8, 0),
                        "1:00 PM" to LocalTime.of(13, 0),
                        "8:00 PM" to LocalTime.of(20, 0),
                        "10:00 PM" to LocalTime.of(22, 0)
                    )
                    presets.forEach { (label, presetTime) ->
                        OutlinedButton(
                            onClick = { viewModel.addTime(presetTime) }
                        ) {
                            Text("+ $label")
                        }
                    }
                }
            }

            // Validation errors if any
            if (uiState.validationErrors.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(Dimens.CardCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Dimens.SpacingMedium)) {
                        uiState.validationErrors.forEach { err ->
                            Text(
                                text = "• $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

            Button(
                onClick = { viewModel.saveSchedule() },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save & Activate Reminders", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
