package org.opencairn.cairn.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.GearKind
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.gear_add
import org.opencairn.cairn.ui.design.resources.gear_brand
import org.opencairn.cairn.ui.design.resources.gear_edit
import org.opencairn.cairn.ui.design.resources.gear_kind
import org.opencairn.cairn.ui.design.resources.gear_model
import org.opencairn.cairn.ui.design.resources.gear_name
import org.opencairn.cairn.ui.design.resources.gear_notes
import org.opencairn.cairn.ui.design.resources.gear_purchase_date
import org.opencairn.cairn.ui.design.resources.gear_save

/** The create/edit form; leaves on its own once the server accepted the item. */
@Composable
fun GearEditScreen(
    viewModel: GearEditViewModel,
    isNew: Boolean,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val d = state.draft
    SubPage(
        title = stringResource(if (isNew) Res.string.gear_add else Res.string.gear_edit),
        onBack = onDone,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.gear_kind), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GearKind.entries.forEach { kind ->
                    FilterChip(
                        selected = d.kind == kind,
                        onClick = { viewModel.update { it.copy(kind = kind) } },
                        label = { Text(kind.label()) },
                    )
                }
            }
            Field(d.name, Res.string.gear_name) { v -> viewModel.update { it.copy(name = v) } }
            Field(d.brand, Res.string.gear_brand) { v -> viewModel.update { it.copy(brand = v) } }
            Field(d.model, Res.string.gear_model) { v -> viewModel.update { it.copy(model = v) } }
            Field(d.purchaseDate, Res.string.gear_purchase_date) { v -> viewModel.update { it.copy(purchaseDate = v) } }
            Field(d.notes, Res.string.gear_notes, singleLine = false) { v -> viewModel.update { it.copy(notes = v) } }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = state.valid && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.gear_save))
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: StringResource,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}
