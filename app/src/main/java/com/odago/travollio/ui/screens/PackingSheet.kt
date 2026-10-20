package com.odago.travollio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.PackingItem
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary

/** The packing list: tick things off as they go into the bag, ask Pablo to fill it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onToggle: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onGenerate: () -> Unit,
    onAskPablo: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Hairline) }
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Luggage, null, tint = AccentDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "Packing list",
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkPrimary,
                    modifier = Modifier.weight(1f)
                )
                state.trip?.let { t ->
                    val packed = state.packing.count { it.isPacked }
                    Text(
                        if (state.packing.isEmpty()) "" else "$packed/${state.packing.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentDeep
                    )
                    Spacer(Modifier.size(1.dp))
                }
            }

            // Progress bar.
            if (state.packing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val ratio = state.packing.count { it.isPacked }.toFloat() / state.packing.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99))
                        .background(Hairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(6.dp)
                            .clip(RoundedCornerShape(99))
                            .background(Accent)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Empty state → generate.
            if (state.packing.isEmpty()) {
                Text(
                    "Nothing on the list yet. Let Travollio's agent draft one from your plans, or add items yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                Spacer(Modifier.height(10.dp))
            }

            com.odago.travollio.ui.components.PrimaryButton(
                label = if (state.packing.isEmpty()) "Generate my packing list" else "Fill in what's missing",
                onClick = onGenerate,
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Add an item — e.g. 'Swim shorts'", color = InkTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentDeep,
                    unfocusedBorderColor = Hairline,
                    cursorColor = AccentDeep
                ),
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = {
                            onAdd(input, "other")
                            input = ""
                        }) {
                            Icon(Icons.Filled.Add, "Add", tint = AccentDeep)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.packing, key = { it.id }) { p ->
                    PackingRow(
                        p = p,
                        onToggle = { onToggle(p.id) },
                        onRemove = { onRemove(p.id) }
                    )
                }
            }

            if (state.packing.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Hairline)
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"Did I pack my charger?\" — just ask Pablo and he'll check the list with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ask the traveller's companion to walk the list",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onAskPablo() }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PackingRow(p: PackingItem, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp)
    ) {
        // Check square.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (p.isPacked) Accent else androidx.compose.ui.graphics.Color.Transparent)
                .then(
                    if (p.isPacked) Modifier
                    else Modifier.border(1.dp, Hairline, RoundedCornerShape(7.dp))
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = if (p.isPacked) "Packed" else "Not packed",
                tint = if (p.isPacked) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (p.isPacked) InkTertiary else InkPrimary,
                textDecoration = if (p.isPacked) TextDecoration.LineThrough else null
            )
            if (p.aiSuggested && !p.isPacked) {
                Text(
                    p.category.replaceFirstChar { it.uppercase() } + " · suggested",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentDeep
                )
            } else {
                Text(
                    p.category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.alpha(0.7f)) {
            Icon(Icons.Filled.Close, "Remove", tint = InkTertiary, modifier = Modifier.size(16.dp))
        }
    }
}
