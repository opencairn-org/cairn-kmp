package org.opencairn.cairn.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Facet
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.model.YearStat
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.analysis_stats
import org.opencairn.cairn.ui.design.resources.overview_distance
import org.opencairn.cairn.ui.design.resources.overview_elevation
import org.opencairn.cairn.ui.design.resources.overview_moving_time
import org.opencairn.cairn.ui.design.resources.overview_total_activities
import org.opencairn.cairn.ui.design.resources.stats_activity_count
import org.opencairn.cairn.ui.design.resources.stats_by_sport
import org.opencairn.cairn.ui.design.resources.stats_by_year
import org.opencairn.cairn.ui.design.resources.stats_empty
import org.opencairn.cairn.ui.design.resources.stats_longest_distance
import org.opencairn.cairn.ui.design.resources.stats_longest_duration

/** Lifetime statistics: totals, one bar per year, the split by sport and the longest activities. */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(stringResource(Res.string.analysis_stats), onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            val stats = state.stats.value
            when {
                stats == null && state.refreshing -> LoadingBox()
                stats == null || stats.totals.count == 0 -> EmptyBox(stringResource(Res.string.stats_empty))
                else -> StatsContent(stats, state, formatters, dates, onOpenActivity)
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: Stats,
    state: StatsUiState,
    f: Formatters,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TotalsGrid(stats.totals, f) }
        if (state.years.isNotEmpty()) {
            item { Title(Res.string.stats_by_year) }
            items(state.years, key = { it.year }) { YearRow(it, state.longestYearMeters, f) }
        }
        if (stats.bySport.isNotEmpty()) {
            item { SportSplit(stats.bySport, stats.totals.count) }
        }
        stats.longestDistance?.let { a ->
            item {
                Longest(
                    Res.string.stats_longest_distance,
                    a,
                    a.summary.distance?.let(f::distance),
                    dates,
                    onOpenActivity,
                )
            }
        }
        stats.longestDuration?.let { a ->
            item { Longest(Res.string.stats_longest_duration, a, f.duration(a.elapsed), dates, onOpenActivity) }
        }
    }
}

@Composable
private fun Title(title: StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun TotalsGrid(
    t: Totals,
    f: Formatters,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_total_activities), t.count.toString(), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_distance), f.distance(t.distance), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_moving_time), f.duration(t.moving), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_elevation), f.elevation(t.elevationGain), Modifier.weight(1f))
        }
    }
}

/** A year with its distance as a bar relative to the farthest year. */
@Composable
private fun YearRow(
    year: YearStat,
    longestMeters: Double,
    f: Formatters,
) {
    val share = if (longestMeters > 0) (year.distance.value / longestMeters).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(year.year.toString(), style = MaterialTheme.typography.bodyLarge)
            Text(f.distance(year.distance), style = MaterialTheme.typography.bodyLarge)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        val details =
            listOf(
                stringResource(Res.string.stats_activity_count, year.count),
                f.duration(year.moving),
                f.elevation(year.elevationGain),
            )
        Text(
            details.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SportSplit(
    sports: List<Facet>,
    total: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Title(Res.string.stats_by_sport)
        sports.sortedByDescending { it.count }.forEach { facet ->
            val type = ActivityType.fromWire(facet.value)
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SportIcon(type, Modifier.size(20.dp))
                Text(
                    if (type == ActivityType.UNSPECIFIED) facet.value else type.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                Text(facet.count.toString(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (total > 0) "${facet.count * 100 / total} %" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp).padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Longest(
    title: StringResource,
    activity: Activity,
    value: String?,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    Column {
        Title(title)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpenActivity(activity.id) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SportIcon(activity.type, Modifier.size(24.dp))
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    activity.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dates.date(activity.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            value?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
        HorizontalDivider()
    }
}
