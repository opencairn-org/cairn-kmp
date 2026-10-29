package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.ui.charts.CombinedStreamChart
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.StatRow
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.detail_avg_cadence
import org.opencairn.cairn.ui.design.resources.detail_avg_hr
import org.opencairn.cairn.ui.design.resources.detail_avg_pace
import org.opencairn.cairn.ui.design.resources.detail_avg_power
import org.opencairn.cairn.ui.design.resources.detail_avg_speed
import org.opencairn.cairn.ui.design.resources.detail_badge_commute
import org.opencairn.cairn.ui.design.resources.detail_badge_ebike
import org.opencairn.cairn.ui.design.resources.detail_badge_race
import org.opencairn.cairn.ui.design.resources.detail_badge_virtual
import org.opencairn.cairn.ui.design.resources.detail_best_efforts
import org.opencairn.cairn.ui.design.resources.detail_calories
import org.opencairn.cairn.ui.design.resources.detail_description
import org.opencairn.cairn.ui.design.resources.detail_distance
import org.opencairn.cairn.ui.design.resources.detail_elapsed_time
import org.opencairn.cairn.ui.design.resources.detail_elevation_gain
import org.opencairn.cairn.ui.design.resources.detail_elevation_loss
import org.opencairn.cairn.ui.design.resources.detail_full_screen
import org.opencairn.cairn.ui.design.resources.detail_gear
import org.opencairn.cairn.ui.design.resources.detail_gear_unset
import org.opencairn.cairn.ui.design.resources.detail_intensity
import org.opencairn.cairn.ui.design.resources.detail_map
import org.opencairn.cairn.ui.design.resources.detail_max_cadence
import org.opencairn.cairn.ui.design.resources.detail_max_elevation
import org.opencairn.cairn.ui.design.resources.detail_max_hr
import org.opencairn.cairn.ui.design.resources.detail_max_power
import org.opencairn.cairn.ui.design.resources.detail_max_speed
import org.opencairn.cairn.ui.design.resources.detail_moving_time
import org.opencairn.cairn.ui.design.resources.detail_normalized_power
import org.opencairn.cairn.ui.design.resources.detail_photos
import org.opencairn.cairn.ui.design.resources.detail_see_all
import org.opencairn.cairn.ui.design.resources.detail_sources
import org.opencairn.cairn.ui.design.resources.detail_streams
import org.opencairn.cairn.ui.design.resources.detail_temperature
import org.opencairn.cairn.ui.design.resources.detail_tss
import org.opencairn.cairn.ui.map.RouteMap

@Composable
fun ActivityDetailScreen(
    viewModel: ActivityDetailViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    units: Units,
    onOpenMap: () -> Unit,
    onOpenStreams: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = state.activity.value
    if (activity == null) {
        if (state.error != null) StaleBanner(state.error, onRetry = viewModel::refresh) else LoadingBox()
        return
    }
    Column {
        if (state.error != null) StaleBanner(state.error, onRetry = viewModel::refresh)
        LazyColumn(
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(activity, dates) }
            item { PrimaryStats(activity, formatters) }
            val stream = state.stream.value
            if (stream != null && stream.hasGps) {
                item {
                    SectionWithAction(Res.string.detail_map, Res.string.detail_full_screen, onOpenMap)
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))) {
                        RouteMap(stream, state.selectedIndex)
                    }
                }
            } else {
                activity.mapImageUrl?.let { url ->
                    item {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            if (stream != null && stream.channels.isNotEmpty()) {
                item {
                    SectionWithAction(Res.string.detail_streams, Res.string.detail_see_all, onOpenStreams)
                    CombinedStreamChart(stream, units, state.selectedIndex, viewModel::select)
                }
            }
            item { SecondaryStats(activity, formatters) }
            val photos = state.attachments.value.orEmpty()
            if (photos.isNotEmpty()) {
                item {
                    Section(Res.string.detail_photos)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { p ->
                            AsyncImage(
                                model = p.thumbUrl ?: p.mediumUrl ?: p.originalUrl,
                                contentDescription = p.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
            val efforts = state.bestEfforts.value.orEmpty()
            if (efforts.isNotEmpty()) {
                item { Section(Res.string.detail_best_efforts) }
                items(efforts, key = { it.id }) { e -> BestEffortRow(e, formatters) }
            }
            if (activity.description.isNotBlank()) {
                item {
                    Section(Res.string.detail_description)
                    Text(activity.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (activity.sources.isNotEmpty()) {
                item {
                    Section(Res.string.detail_sources)
                    activity.sources.forEach { s ->
                        val primary = if (s.id == activity.primaryStreamSourceId) " · primary" else ""
                        val imported = s.importedAt?.let { " · ${dates.date(it)}" }.orEmpty()
                        Text(
                            "${s.provider} · ${s.status}$primary · ${s.externalId}$imported",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionWithAction(
    title: StringResource,
    action: StringResource,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction) { Text(stringResource(action)) }
    }
}

@Composable
private fun Section(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Header(
    a: Activity,
    dates: DateFormatters,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SportIcon(a.type, Modifier.size(28.dp))
            Text(a.displayTitle, style = MaterialTheme.typography.headlineSmall)
        }
        val subtype = listOf(a.discipline, a.customSubtype).filter { it.isNotBlank() }.joinToString(" · ")
        val line =
            listOf(
                subtype.ifBlank { a.type.label() },
                dates.dateTime(a.startTime),
                a.startPlace.ifBlank { a.timezone },
            ).filter { it.isNotBlank() }.joinToString(" · ")
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (a.isRace) AssistChip(onClick = {}, label = { Text(stringResource(Res.string.detail_badge_race)) })
            if (a.isVirtual) AssistChip(onClick = {}, label = { Text(stringResource(Res.string.detail_badge_virtual)) })
            if (a.isCommute) AssistChip(onClick = {}, label = { Text(stringResource(Res.string.detail_badge_commute)) })
            if (a.isEbike) AssistChip(onClick = {}, label = { Text(stringResource(Res.string.detail_badge_ebike)) })
        }
        val gear = a.gearName ?: if (a.gearUnset) stringResource(Res.string.detail_gear_unset) else null
        gear?.let {
            Text("${stringResource(Res.string.detail_gear)}: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrimaryStats(
    a: Activity,
    f: Formatters,
) {
    val s = a.summary
    val moving = a.moving.takeIf { it.inWholeSeconds > 0 } ?: a.elapsed
    val tiles =
        buildList {
            s.distance?.let { add(Res.string.detail_distance to f.distance(it)) }
            add(Res.string.detail_moving_time to f.duration(moving))
            s.avgSpeed?.let {
                if (a.type.usesPace) {
                    add(Res.string.detail_avg_pace to f.pace(it))
                } else {
                    add(Res.string.detail_avg_speed to f.speed(it))
                }
            }
            s.elevationGain?.let { add(Res.string.detail_elevation_gain to f.elevation(it)) }
        }
    // Two tiles per row keeps the values on one line on phones.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) -> StatTile(stringResource(label), value, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SecondaryStats(
    a: Activity,
    f: Formatters,
) {
    val s = a.summary
    val rows =
        buildList {
            add(Res.string.detail_elapsed_time to f.duration(a.elapsed))
            s.maxSpeed?.let { add(Res.string.detail_max_speed to if (a.type.usesPace) f.pace(it) else f.speed(it)) }
            s.avgHeartRate?.let { add(Res.string.detail_avg_hr to "$it bpm") }
            s.maxHeartRate?.let { add(Res.string.detail_max_hr to "$it bpm") }
            s.avgPower?.let { add(Res.string.detail_avg_power to "$it W") }
            s.maxPower?.let { add(Res.string.detail_max_power to "$it W") }
            s.normalizedPower?.let { add(Res.string.detail_normalized_power to "$it W") }
            s.avgCadence?.let { add(Res.string.detail_avg_cadence to "$it rpm") }
            s.maxCadence?.let { add(Res.string.detail_max_cadence to "$it rpm") }
            s.calories?.let { add(Res.string.detail_calories to "$it kcal") }
            s.elevationLoss?.let { add(Res.string.detail_elevation_loss to f.elevation(it)) }
            s.maxElevation?.let { add(Res.string.detail_max_elevation to f.elevation(it)) }
            s.tss?.let { add(Res.string.detail_tss to it.toInt().toString()) }
            s.intensityFactor?.let { add(Res.string.detail_intensity to "${(it * 100).toInt()} %") }
            s.avgTemperatureC?.let { add(Res.string.detail_temperature to f.temperature(it)) }
        }
    Column {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label, value) -> StatRow(stringResource(label), value, Modifier.weight(1f)) }
                if (pair.size == 1) {
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BestEffortRow(
    e: BestEffort,
    f: Formatters,
) {
    val window =
        when (e.windowKind) {
            BestEffortWindowKind.DISTANCE -> f.distance(Meters(e.windowValue.toDouble()))
            BestEffortWindowKind.DURATION -> f.duration(kotlin.time.Duration.parse("${e.windowValue}s"))
        }
    val value =
        when (e.metric) {
            BestEffortMetric.PACE -> f.pace(MetersPerSecond(if (e.achievedValue > 0) 1000.0 / e.achievedValue else 0.0))
            BestEffortMetric.SPEED -> f.speed(MetersPerSecond(e.achievedValue))
            BestEffortMetric.POWER -> "${e.achievedValue.toInt()} W"
            BestEffortMetric.HEART_RATE -> "${e.achievedValue.toInt()} bpm"
            BestEffortMetric.VAM -> "${e.achievedValue.toInt()} m/h"
        }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${e.metric.name.lowercase().replace('_', ' ')} · $window",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
    }
}
