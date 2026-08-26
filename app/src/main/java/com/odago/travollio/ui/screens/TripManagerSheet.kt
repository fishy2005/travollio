package com.odago.travollio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.odago.travollio.data.Currencies
import com.odago.travollio.data.Currency
import com.odago.travollio.data.Trip
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
                                "${formatDate(t.startDate)} → ${formatDate(t.endDate)} · ${t.baseCity}",
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
    var home by remember { mutableStateOf(Currencies.byCode(initial.homeCcy)) }
    var local by remember { mutableStateOf(Currencies.byCode(initial.localCcy)) }
    var dateError by remember { mutableStateOf(false) }

    fun validInput(): Boolean {
        val s = runCatching { LocalDate.parse(start) }.getOrNull()
        val e = runCatching { LocalDate.parse(end) }.getOrNull()
        // A new trip must have a name and full, ordered dates; editing may leave them blank to keep current.
        val datesOk = if (isNew) s != null && e != null && !e.isBefore(s) else (s == null || e == null || !e.isBefore(s))
        val nameOk = !isNew || name.trim().isNotBlank()
        return nameOk && datesOk
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(if (isNew) "New trip" else "Edit trip", color = InkPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.size(10.dp))
                TripDateField("Start date", start) { start = it }
                Spacer(Modifier.size(10.dp))
                TripDateField("End date", end) { end = it }
                if (dateError) Text(
                    "Enter a trip name and valid dates (end on or after start).",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(base, { base = it }, label = { Text("Base city") }, singleLine = true)
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    budget, { budget = it }, label = { Text("Budget cap") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.size(10.dp))
                CurrencyPickerField("Home currency", home) { home = it }
                Spacer(Modifier.size(10.dp))
                CurrencyPickerField("Local currency", local) { local = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!validInput()) { dateError = true; return@TextButton }
                dateError = false
                val trip = initial.copy(
                    name = name.trim().ifBlank { initial.name },
                    startDate = start.ifBlank { initial.startDate },
                    endDate = end.ifBlank { initial.endDate },
                    baseCity = base.trim().ifBlank { initial.baseCity },
                    budgetCapHomeCcy = budget.trim().toDoubleOrNull() ?: initial.budgetCapHomeCcy,
                    homeCcy = home.code,
                    localCcy = local.code
                )
                onSave(trip)
            }) { Text("Save", color = AccentDeep) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) } }
    )
}

/** A tappable field that opens a Material date picker; value stays as ISO yyyy-MM-dd. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDateField(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Text(
                        if (value.isBlank()) "Pick a date" else formatDate(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = InkSecondary)
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (runCatching { LocalDate.parse(value) }.getOrNull()
                ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK", color = AccentDeep) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = InkSecondary) } }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** A tappable field that opens a searchable currency list (ISO code + name + symbol). */
@Composable
private fun CurrencyPickerField(label: String, value: Currency, onSelected: (Currency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(
                    "${value.code} — ${value.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = InkSecondary)
        }
    }
    if (open) {
        CurrencyPickerDialog(value, onDismiss = { open = false }, onSelect = { onSelected(it); open = false })
    }
}

@Composable
private fun CurrencyPickerDialog(current: Currency, onDismiss: () -> Unit, onSelect: (Currency) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = Currencies.all.filter {
        query.isBlank() ||
            it.code.contains(query.trim(), ignoreCase = true) ||
            it.name.contains(query.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Choose currency", color = InkPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Search code or name") }, singleLine = true
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 4.dp)) {
                    items(filtered, key = { it.code }) { c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onSelect(c) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${c.code} — ${c.name}", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                            }
                            Text(c.symbol, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                            if (c.code == current.code) Text("   ✓", color = AccentDeep)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) } }
    )
}
