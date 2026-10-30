package org.opencairn.cairn.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearKind
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_gear
import org.opencairn.cairn.ui.design.resources.gear_add
import org.opencairn.cairn.ui.design.resources.gear_empty
import org.opencairn.cairn.ui.design.resources.gear_kind_bike
import org.opencairn.cairn.ui.design.resources.gear_kind_other
import org.opencairn.cairn.ui.design.resources.gear_kind_shoe
import org.opencairn.cairn.ui.design.resources.gear_retired

fun GearKind.icon(): ImageVector =
    when (this) {
        GearKind.BIKE -> Icons.Default.PedalBike
        GearKind.SHOE -> Icons.AutoMirrored.Filled.DirectionsRun
        GearKind.OTHER -> Icons.Default.Inventory2
    }

@Composable
fun GearKind.label(): String =
    when (this) {
        GearKind.BIKE -> stringResource(Res.string.gear_kind_bike)
        GearKind.SHOE -> stringResource(Res.string.gear_kind_shoe)
        GearKind.OTHER -> stringResource(Res.string.gear_kind_other)
    }

/** Active gear with lifetime numbers, retired gear folded away, and a button to add. */
@Composable
fun GearScreen(
    viewModel: GearViewModel,
    formatters: Formatters,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRetired by rememberSaveable { mutableStateOf(false) }
    SubPage(
        title = stringResource(Res.string.account_gear),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.gear_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            if (state.gear.value?.isEmpty() == true) {
                EmptyBox(stringResource(Res.string.gear_empty))
                return@Column
            }
            LazyColumn {
                items(state.active, key = { it.id }) { GearRow(it, formatters) { onOpen(it.id) } }
                if (state.retired.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { showRetired = !showRetired },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Icon(
                                if (showRetired) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                            )
                            Text("${stringResource(Res.string.gear_retired)} (${state.retired.size})")
                        }
                    }
                    if (showRetired) {
                        items(state.retired, key = { it.id }) { GearRow(it, formatters) { onOpen(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun GearRow(
    gear: Gear,
    f: Formatters,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(gear.name) },
        supportingContent = {
            Text(
                listOf(gear.subtitle, f.distance(gear.totalDistance), "${gear.activityCount} ×")
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        },
        leadingContent = { Icon(gear.kind.icon(), contentDescription = gear.kind.label()) },
        trailingContent =
            if (gear.retired) {
                { Text(stringResource(Res.string.gear_retired), style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
