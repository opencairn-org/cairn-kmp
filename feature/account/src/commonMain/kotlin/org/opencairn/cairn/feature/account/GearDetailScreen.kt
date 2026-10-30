package org.opencairn.cairn.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.ui.design.components.StatRow
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.action_cancel
import org.opencairn.cairn.ui.design.resources.gear_activities
import org.opencairn.cairn.ui.design.resources.gear_default_for
import org.opencairn.cairn.ui.design.resources.gear_delete
import org.opencairn.cairn.ui.design.resources.gear_delete_confirm
import org.opencairn.cairn.ui.design.resources.gear_distance
import org.opencairn.cairn.ui.design.resources.gear_edit
import org.opencairn.cairn.ui.design.resources.gear_notes
import org.opencairn.cairn.ui.design.resources.gear_providers
import org.opencairn.cairn.ui.design.resources.gear_retire
import org.opencairn.cairn.ui.design.resources.gear_retired_at
import org.opencairn.cairn.ui.design.resources.gear_since
import org.opencairn.cairn.ui.design.resources.gear_time
import org.opencairn.cairn.ui.design.resources.gear_unretire

/** One gear item: lifetime numbers, provenance, notes, and retire or delete; editing opens the form. */
@Composable
fun GearDetailScreen(
    viewModel: GearViewModel,
    gearId: String,
    formatters: Formatters,
    dates: DateFormatters,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gear = state.byId(gearId)
    LaunchedEffect(state.deletedId) { if (state.deletedId == gearId) onBack() }
    var confirmDelete by remember { mutableStateOf(false) }
    SubPage(
        title = gear?.name.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = onEdit, enabled = gear != null) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.gear_edit))
            }
        },
    ) { padding ->
        if (gear == null) return@SubPage
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                listOf(gear.kind.label(), gear.subtitle)
                    .filter {
                        it.isNotBlank()
                    }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    stringResource(Res.string.gear_distance),
                    formatters.distance(gear.totalDistance),
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(Res.string.gear_time),
                    formatters.duration(gear.totalMoving),
                    Modifier.weight(1f),
                )
                StatTile(stringResource(Res.string.gear_activities), gear.activityCount.toString(), Modifier.weight(1f))
            }
            if (gear.purchaseDate.isNotBlank()) {
                StatRow(
                    stringResource(Res.string.gear_since),
                    dates.isoDate(gear.purchaseDate),
                )
            }
            gear.retiredAt?.let { StatRow(stringResource(Res.string.gear_retired_at), dates.date(it)) }
            if (gear.defaultFor.isNotEmpty()) {
                StatRow(stringResource(Res.string.gear_default_for), gear.defaultFor.joinToString { it.label() })
            }
            if (gear.providers.isNotEmpty()) {
                StatRow(
                    stringResource(Res.string.gear_providers),
                    gear.providers.joinToString { p ->
                        listOf(p.provider, p.providerName).filter { it.isNotBlank() }.joinToString(": ")
                    },
                )
            }
            if (gear.notes.isNotBlank()) StatRow(stringResource(Res.string.gear_notes), gear.notes)
            Actions(gear, state.busy, onRetire = { viewModel.setRetired(gear.id, !gear.retired) }, onDelete = {
                confirmDelete =
                    true
            })
        }
    }
    if (confirmDelete && gear != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(Res.string.gear_delete_confirm, gear.name, gear.activityCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(gear.id)
                }) { Text(stringResource(Res.string.gear_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Actions(
    gear: Gear,
    busy: Boolean,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRetire, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (gear.retired) Res.string.gear_unretire else Res.string.gear_retire))
        }
        OutlinedButton(onClick = onDelete, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.gear_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}
