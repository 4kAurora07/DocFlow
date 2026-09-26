package com.doseflow.app.ui.medicine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doseflow.app.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicineScreen(
    medicineId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToScheduleEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditMedicineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(medicineId) {
        viewModel.loadMedicine(medicineId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            val savedId = uiState.medicineId
            if (medicineId == null || medicineId == "new") {
                // If this is a new medicine, take user straight to schedule editor!
                if (savedId != null) {
                    onNavigateToScheduleEditor(savedId)
                } else {
                    onNavigateBack()
                }
            } else {
                onNavigateBack()
            }
        }
    }

    val isEditing = medicineId != null && medicineId != "new"
    val units = listOf("mg", "ml", "tablet", "capsule", "drops", "puffs", "units")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Medication" else "Add Medication",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Medication Name *") },
                placeholder = { Text("e.g. Amoxicillin, Lisinopril") },
                isError = uiState.errorMessage != null,
                supportingText = uiState.errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.dosage,
                onValueChange = { viewModel.onDosageChange(it) },
                label = { Text("Dosage Amount") },
                placeholder = { Text("e.g. 500, 10, 2.5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                units.forEach { unitItem ->
                    FilterChip(
                        selected = uiState.unit.equals(unitItem, ignoreCase = true),
                        onClick = { viewModel.onUnitChange(unitItem) },
                        label = { Text(unitItem) }
                    )
                }
            }

            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = { viewModel.onInstructionsChange(it) },
                label = { Text("Instructions / Notes") },
                placeholder = { Text("e.g. Take with food, drink full glass of water") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

            Button(
                onClick = { viewModel.saveMedicine() },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = if (isEditing) "Save Changes" else "Save & Set Schedule",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isEditing && uiState.medicineId != null) {
                OutlinedButton(
                    onClick = { onNavigateToScheduleEditor(uiState.medicineId!!) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                ) {
                    Text("Configure Reminders Schedule")
                }
            }
        }
    }
}
