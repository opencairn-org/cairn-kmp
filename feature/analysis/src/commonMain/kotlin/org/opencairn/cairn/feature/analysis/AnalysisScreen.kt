package org.opencairn.cairn.feature.analysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import org.opencairn.cairn.ui.charts.LoadLegendItem
import org.opencairn.cairn.ui.charts.LoadSeries
import org.opencairn.cairn.ui.charts.TrainingLoadChart
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.analysis_fatigue
import org.opencairn.cairn.ui.design.resources.analysis_fitness
import org.opencairn.cairn.ui.design.resources.analysis_form
import org.opencairn.cairn.ui.design.resources.analysis_heatmap
import org.opencairn.cairn.ui.design.resources.analysis_heatmap_hint
import org.opencairn.cairn.ui.design.resources.analysis_load
import org.opencairn.cairn.ui.design.resources.analysis_load_empty
import org.opencairn.cairn.ui.design.resources.analysis_load_hint
import org.opencairn.cairn.ui.design.resources.analysis_stats
import org.opencairn.cairn.ui.design.resources.analysis_stats_hint
import org.opencairn.cairn.ui.design.resources.nav_analysis
import org.opencairn.cairn.ui.design.resources.range_all
import org.opencairn.cairn.ui.design.resources.range_one_year
import org.opencairn.cairn.ui.design.resources.range_six_months
import org.opencairn.cairn.ui.design.resources.range_three_months
import kotlin.math.roundToInt

/** The analysis tab: training load up front, statistics and the heatmap one tap away. */
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    dates: DateFormatters,
    onOpenStats: () -> Unit,
    onOpenHeatmap: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(Res.string.nav_analysis),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { TrainingLoadCard(state, dates, viewModel::setRange, viewModel::select) }
            item {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(
                        Icons.Default.BarChart,
                        Res.string.analysis_stats,
                        Res.string.analysis_stats_hint,
                        onOpenStats,
                    )
                    NavRow(
                        Icons.Default.Map,
                        Res.string.analysis_heatmap,
                        Res.string.analysis_heatmap_hint,
                        onOpenHeatmap,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingLoadCard(
    state: AnalysisUiState,
    dates: DateFormatters,
    onRange: (TrainingLoadRange) -> Unit,
    onSelect: (Int?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.analysis_load), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingLoadRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { onRange(range) },
                    label = { Text(stringResource(range.label())) },
                )
            }
        }
        val day = state.shownDay
        if (!state.hasData || day == null) {
            if (!state.refreshing) {
                Text(
                    stringResource(Res.string.analysis_load_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LoadTiles(day)
            Text(
                dates.isoDate(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrainingLoadChart(
                state.load.value
                    ?.days
                    .orEmpty(),
                state.selected,
                onSelect,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadLegendItem(LoadSeries.FITNESS, stringResource(Res.string.analysis_fitness))
                LoadLegendItem(LoadSeries.FATIGUE, stringResource(Res.string.analysis_fatigue))
                LoadLegendItem(LoadSeries.FORM, stringResource(Res.string.analysis_form))
            }
            Text(
                stringResource(Res.string.analysis_load_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadTiles(day: TrainingLoadDay) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(Res.string.analysis_fitness), day.fitness.roundToInt().toString(), Modifier.weight(1f))
        StatTile(stringResource(Res.string.analysis_fatigue), day.fatigue.roundToInt().toString(), Modifier.weight(1f))
        StatTile(stringResource(Res.string.analysis_form), signed(day.form.roundToInt()), Modifier.weight(1f))
    }
}

/** Form reads as a balance: `+7`, `-12`, `0`. */
internal fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

private fun TrainingLoadRange.label(): StringResource =
    when (this) {
        TrainingLoadRange.THREE_MONTHS -> Res.string.range_three_months
        TrainingLoadRange.SIX_MONTHS -> Res.string.range_six_months
        TrainingLoadRange.ONE_YEAR -> Res.string.range_one_year
        TrainingLoadRange.ALL -> Res.string.range_all
    }

@Composable
private fun NavRow(
    icon: ImageVector,
    title: StringResource,
    hint: StringResource,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
