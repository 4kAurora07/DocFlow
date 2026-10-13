package com.doseflow.app.ui.medicine

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.doseflow.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doseflow.app.domain.model.Medicine
import com.doseflow.app.theme.Dimens
import com.doseflow.app.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(
    onNavigateToAddMedicine: () -> Unit,
    onNavigateToEditMedicine: (String) -> Unit,
    onNavigateToScheduleEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var medicineToDelete by remember { mutableStateOf<Medicine?>(null) }

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
                        text = "Medicines",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleArchivedFilter() }) {
                        Text(if (uiState.showArchived) "Show Active" else "Show Archived")
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
        val filteredList = uiState.medicines.filter {
            if (uiState.showArchived) it.medicine.isArchived else !it.medicine.isArchived
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = if (uiState.showArchived) "No Archived Medicines" else "No Medicines Added",
                    description = if (uiState.showArchived) "You haven't archived any medications." else "Add your medications to configure reliable reminder schedules.",
                    actionLabel = if (uiState.showArchived) null else "Add Medicine",
                    onActionClick = if (uiState.showArchived) null else onNavigateToAddMedicine
                )
            }
        } else {
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
                items(
                    items = filteredList,
                    key = { it.medicine.id }
                ) { item ->
                    MedicineCard(
                        medicine = item.medicine,
                        onEditClick = { onNavigateToEditMedicine(item.medicine.id) },
                        onScheduleClick = { onNavigateToScheduleEditor(item.medicine.id) },
                        onArchiveToggle = { archive -> viewModel.archiveMedicine(item.medicine.id, archive) },
                        onDeleteClick = { medicineToDelete = item.medicine }
                    )
                }
            }
        }
    }

    medicineToDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { medicineToDelete = null },
            title = { Text("Delete ${med.name}?") },
            text = { Text("Deleting this medication will permanently remove all associated schedules and pending reminders.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedicine(med.id)
                        medicineToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { medicineToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MedicineCard(
    medicine: Medicine,
    onEditClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onArchiveToggle: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
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
                        text = medicine.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val dosageText = if (medicine.dosage.isNotBlank()) "${medicine.dosage} ${medicine.unit}" else ""
                    if (dosageText.isNotBlank()) {
                        Text(
                            text = dosageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Medicine")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Medicine",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (medicine.instructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                Text(
                    text = medicine.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                OutlinedButton(
                    onClick = onScheduleClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                    Text("Schedule")
                }

                TextButton(
                    onClick = { onArchiveToggle(!medicine.isArchived) }
                ) {
                    Text(if (medicine.isArchived) "Unarchive" else "Archive")
                }
            }
        }
    }
}
