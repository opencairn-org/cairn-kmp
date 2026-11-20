package org.opencairn.cairn.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.DateFormat
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.TimeFormat
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.ui.design.components.StatRow
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.approve_device_title
import org.opencairn.cairn.ui.design.resources.settings_account
import org.opencairn.cairn.ui.design.resources.settings_date_format
import org.opencairn.cairn.ui.design.resources.settings_devices
import org.opencairn.cairn.ui.design.resources.settings_format
import org.opencairn.cairn.ui.design.resources.settings_locale_default
import org.opencairn.cairn.ui.design.resources.settings_server
import org.opencairn.cairn.ui.design.resources.settings_time_format
import org.opencairn.cairn.ui.design.resources.settings_title
import org.opencairn.cairn.ui.design.resources.settings_units
import org.opencairn.cairn.ui.design.resources.settings_units_imperial
import org.opencairn.cairn.ui.design.resources.settings_units_metric
import org.opencairn.cairn.ui.design.resources.settings_update_check
import org.opencairn.cairn.ui.design.resources.settings_update_check_hint
import org.opencairn.cairn.ui.design.resources.settings_updates

/** Account facts, editable format and units, the server, and device approval. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    user: User?,
    account: AccountKey?,
    updates: UpdateSetting?,
    onBack: () -> Unit,
    onApproveDevice: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(title = stringResource(Res.string.settings_title), onBack = onBack) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(Res.string.settings_account)
            user?.let {
                StatRow("Name", it.displayName)
                StatRow("Username", it.username)
                StatRow("E-mail", it.email)
            }
            Section(Res.string.settings_format)
            user?.let { FormatEditor(it.preferences, state.saving, viewModel::save) }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Section(Res.string.settings_server)
            account?.let { StatRow("URL", it.serverBaseUrl) }
            state.server?.version?.let { StatRow("cairn-core", it) }
            Section(Res.string.settings_devices)
            OutlinedButton(onClick = onApproveDevice) { Text(stringResource(Res.string.approve_device_title)) }
            updates?.let { UpdateCheck(it) }
        }
    }
}

@Composable
private fun UpdateCheck(setting: UpdateSetting) {
    Section(Res.string.settings_updates)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_update_check), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.settings_update_check_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = setting.enabled, onCheckedChange = setting.onChange)
    }
}

@Composable
private fun Section(title: org.jetbrains.compose.resources.StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FormatEditor(
    prefs: Preferences,
    saving: Boolean,
    onSave: (Preferences) -> Unit,
) {
    val localeDefault = stringResource(Res.string.settings_locale_default)
    Choice(
        label = stringResource(Res.string.settings_units),
        options = Units.entries,
        selected = prefs.units,
        name = {
            if (it ==
                Units.METRIC
            ) {
                stringResource(Res.string.settings_units_metric)
            } else {
                stringResource(Res.string.settings_units_imperial)
            }
        },
        enabled = !saving,
    ) { onSave(prefs.copy(units = it)) }
    Choice(
        label = stringResource(Res.string.settings_date_format),
        options = DateFormat.entries,
        selected = prefs.dateFormat,
        name = { if (it == DateFormat.LOCALE) localeDefault else it.name },
        enabled = !saving,
    ) { onSave(prefs.copy(dateFormat = it)) }
    Choice(
        label = stringResource(Res.string.settings_time_format),
        options = TimeFormat.entries,
        selected = prefs.timeFormat,
        name = {
            when (it) {
                TimeFormat.LOCALE -> localeDefault
                TimeFormat.H24 -> "24 h"
                TimeFormat.H12 -> "12 h"
            }
        },
        enabled = !saving,
    ) { onSave(prefs.copy(timeFormat = it)) }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<T>,
    selected: T,
    name: @Composable (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { if (option != selected) onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    enabled = enabled,
                ) { Text(name(option)) }
            }
        }
    }
}
