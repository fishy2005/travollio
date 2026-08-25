package com.odago.travollio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.Trip
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary

/** Trips hub: switch the active trip, start/edit/archive/delete trips — no need to go through chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripManagerSheet(
    trips: List<Trip>,
    activeTripId: Long?,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onCreate: (Trip) -> Unit,
    onUpdate: (Trip) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<Trip?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Trip?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Hairline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            Text("Trips", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
            Spacer(Modifier.size(12.dp))
            if (trips.isEmpty()) {
                Text("No trips yet — create one below.", color = InkSecondary)
            } else {
                trips.forEach { t ->
                    val active = t.id == activeTripId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!active) onSwitch(t.id) }
                            .padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when { active -> "ACTIVE"; t.isArchived -> "ARCHIVED"; else -> "SAVED" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) AccentDeep else InkTertiary
                                )
                            }
                            Text(
                                "${t.startDate} → ${t.endDate} · ${t.baseCity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        if (active && !t.isArchived) {
                            TextButton(onClick = { onArchive(t.id) }) { Text("Archive", color = InkSecondary) }
                        }
                        IconButton(onClick = { editing = t }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = InkSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deleteTarget = t }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = InkSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
            TextButton(onClick = { creating = true }) { Text("New trip", color = AccentDeep) }
        }
    }

    if (creating || editing != null) {
        TripFormDialog(
            initial = editing ?: Trip(
                name = "", startDate = "", endDate = "", baseCity = "",
                budgetCapHomeCcy = 0.0, homeCcy = "ILS", localCcy = "JPY"
            ),
            isNew = creating,
            onDismiss = { creating = false; editing = null },
            onSave = { trip ->
                if (creating) onCreate(trip) else onUpdate(trip)
                creating = false; editing = null
            }
        )
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Delete trip?", color = InkPrimary) },
            text = { Text("Permanently delete '${t.name}' and all its days/items/spend?", color = InkSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(t.id); deleteTarget = null }) { Text("Delete", color = AccentDeep) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = InkSecondary) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripFormDialog(initial: Trip, isNew: Boolean, onDismiss: () -> Unit, onSave: (Trip) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.startDate) }
    var end by remember { mutableStateOf(initial.endDate) }
    var base by remember { mutableStateOf(initial.baseCity) }
    var budget by remember { mutableStateOf(if (initial.budgetCapHomeCcy > 0) initial.budgetCapHomeCcy.toString() else "") }
    var home by remember { mutableStateOf(initial.homeCcy) }
    var local by remember { mutableStateOf(initial.localCcy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(if (isNew) "New trip" else "Edit trip", color = InkPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text("Start (yyyy-MM-dd)") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("End (yyyy-MM-dd)") }, singleLine = true)
                OutlinedTextField(base, { base = it }, label = { Text("Base city") }, singleLine = true)
                OutlinedTextField(
                    budget, { budget = it }, label = { Text("Budget cap") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(home, { home = it }, label = { Text("Home currency") }, singleLine = true)
                OutlinedTextField(local, { local = it }, label = { Text("Local currency") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trip = initial.copy(
                    name = name.trim().ifBlank { initial.name },
                    startDate = start.trim().ifBlank { initial.startDate },
                    endDate = end.trim().ifBlank { initial.endDate },
                    baseCity = base.trim().ifBlank { initial.baseCity },
                    budgetCapHomeCcy = budget.trim().toDoubleOrNull() ?: initial.budgetCapHomeCcy,
                    homeCcy = home.trim().uppercase().ifBlank { initial.homeCcy },
                    localCcy = local.trim().uppercase().ifBlank { initial.localCcy }
                )
                onSave(trip)
            }) { Text("Save", color = AccentDeep) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) } }
    )
}
