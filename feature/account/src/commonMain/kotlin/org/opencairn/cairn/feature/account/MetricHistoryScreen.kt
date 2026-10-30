package org.opencairn.cairn.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.athlete_add
import org.opencairn.cairn.ui.design.resources.athlete_date
import org.opencairn.cairn.ui.design.resources.athlete_delete
import org.opencairn.cairn.ui.design.resources.athlete_empty
import org.opencairn.cairn.ui.design.resources.athlete_history
import org.opencairn.cairn.ui.design.resources.athlete_range
import org.opencairn.cairn.ui.design.resources.athlete_value
import kotlin.time.Clock

/** One parameter: an add form on top (date defaults to today), then every dated value, newest first. */
@Composable
fun MetricHistoryScreen(
    viewModel: AthleteViewModel,
    metricKey: String,
    dates: DateFormatters,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile.value
    val spec = profile?.spec(metricKey)
    SubPage(title = spec?.label ?: metricKey, onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            if (spec != null) {
                AddForm(spec, state.busy) { date, value -> viewModel.add(spec.key, date, value) }
            }
            val history = profile?.history(metricKey).orEmpty()
            if (history.isEmpty()) {
                EmptyBox(stringResource(Res.string.athlete_empty))
                return@Column
            }
            Text(
                stringResource(Res.string.athlete_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(history, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(spec?.format(entry.value) ?: entry.value.toString()) },
                        supportingContent = { Text(dates.isoDate(entry.effectiveDate)) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.delete(entry.id) }, enabled = !state.busy) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.athlete_delete),
                                )
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddForm(
    spec: AthleteMetricSpec,
    busy: Boolean,
    onAdd: (String, Double) -> Unit,
) {
    var date by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()) }
    var value by remember { mutableStateOf("") }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed >= spec.min && parsed <= spec.max && DATE.matches(date)
    val (lo, hi) = spec.rangeLabel()
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text(stringResource(Res.string.athlete_date)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("${stringResource(Res.string.athlete_value)} (${spec.unit})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(Res.string.athlete_range, lo, hi, spec.unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                onAdd(date, requireNotNull(parsed))
                value = ""
            },
            enabled = valid && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.athlete_add)) }
    }
}

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
