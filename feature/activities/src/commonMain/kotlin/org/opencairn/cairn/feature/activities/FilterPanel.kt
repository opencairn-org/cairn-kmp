package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.ActivityFacets
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.filter_clear
import org.opencairn.cairn.ui.design.resources.filter_commute
import org.opencairn.cairn.ui.design.resources.filter_discipline
import org.opencairn.cairn.ui.design.resources.filter_ebike
import org.opencairn.cairn.ui.design.resources.filter_flags
import org.opencairn.cairn.ui.design.resources.filter_period
import org.opencairn.cairn.ui.design.resources.filter_race
import org.opencairn.cairn.ui.design.resources.filter_sort
import org.opencairn.cairn.ui.design.resources.filter_sport
import org.opencairn.cairn.ui.design.resources.filter_virtual
import org.opencairn.cairn.ui.design.resources.filter_year
import org.opencairn.cairn.ui.design.resources.sort_date_asc
import org.opencairn.cairn.ui.design.resources.sort_date_desc
import org.opencairn.cairn.ui.design.resources.sort_distance_desc
import org.opencairn.cairn.ui.design.resources.sort_duration_desc
import org.opencairn.cairn.ui.design.resources.sort_elevation_desc
import org.opencairn.cairn.ui.design.resources.sort_speed_desc
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Sport, discipline and year facets, date presets, flags and sort; mirrors the web filter bar. */
@Composable
fun FilterPanel(
    filter: ActivityFilter,
    sort: ActivitySort,
    facets: ActivityFacets?,
    onFilter: (ActivityFilter) -> Unit,
    onSort: (ActivitySort) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChipRow(stringResource(Res.string.filter_sport)) {
            facets?.types.orEmpty().forEach { facet ->
                val type = ActivityType.fromWire(facet.value)
                FilterChip(
                    selected = type in filter.types,
                    onClick = { onFilter(filter.copy(types = filter.types.toggle(type))) },
                    label = { Text("${type.label()} (${facet.count})") },
                )
            }
        }
        if (!facets?.disciplines.isNullOrEmpty()) {
            ChipRow(stringResource(Res.string.filter_discipline)) {
                facets.disciplines.forEach { facet ->
                    FilterChip(
                        selected = facet.value in filter.disciplines,
                        onClick = { onFilter(filter.copy(disciplines = filter.disciplines.toggle(facet.value))) },
                        label = { Text("${facet.value} (${facet.count})") },
                    )
                }
            }
        }
        ChipRow(stringResource(Res.string.filter_period)) {
            listOf("7d" to 7, "30d" to 30, "90d" to 90, "1y" to 365).forEach { (label, days) ->
                val from = Clock.System.now() - days.days
                val active = filter.from != null && filter.to == null && (filter.from!! - from).absoluteValue < 1.days
                FilterChip(
                    selected = active,
                    onClick = { onFilter(filter.copy(from = if (active) null else from, to = null)) },
                    label = { Text(label) },
                )
            }
        }
        if (!facets?.years.isNullOrEmpty()) {
            ChipRow(stringResource(Res.string.filter_year)) {
                facets.years.forEach { facet ->
                    val year = facet.value.toIntOrNull() ?: return@forEach
                    val from = LocalDate(year, 1, 1).atStartOfDayIn(TimeZone.UTC)
                    val to = LocalDate(year + 1, 1, 1).atStartOfDayIn(TimeZone.UTC)
                    val active = filter.from == from && filter.to == to
                    FilterChip(
                        selected = active,
                        onClick = {
                            onFilter(
                                if (active) filter.copy(from = null, to = null) else filter.copy(from = from, to = to),
                            )
                        },
                        label = { Text("${facet.value} (${facet.count})") },
                    )
                }
            }
        }
        ChipRow(stringResource(Res.string.filter_flags)) {
            FlagChip(stringResource(Res.string.filter_virtual), filter.virtual) { onFilter(filter.copy(virtual = it)) }
            FlagChip(stringResource(Res.string.filter_ebike), filter.ebike) { onFilter(filter.copy(ebike = it)) }
            FlagChip(stringResource(Res.string.filter_commute), filter.commute) { onFilter(filter.copy(commute = it)) }
            FlagChip(stringResource(Res.string.filter_race), filter.race) { onFilter(filter.copy(race = it)) }
        }
        ChipRow(stringResource(Res.string.filter_sort)) {
            ActivitySort.entries.forEach { s ->
                FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(sortLabel(s)) })
            }
        }
        if (!filter.isEmpty || sort != ActivitySort.DATE_DESC) {
            TextButton(onClick = onClear) { Text(stringResource(Res.string.filter_clear)) }
        }
    }
}

@Composable
private fun ChipRow(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** any → only → exclude → any. */
@Composable
private fun FlagChip(
    label: String,
    value: ActivityFilter.Flag,
    onChange: (ActivityFilter.Flag) -> Unit,
) {
    val next =
        when (value) {
            ActivityFilter.Flag.ANY -> ActivityFilter.Flag.ONLY
            ActivityFilter.Flag.ONLY -> ActivityFilter.Flag.EXCLUDE
            ActivityFilter.Flag.EXCLUDE -> ActivityFilter.Flag.ANY
        }
    val text =
        when (value) {
            ActivityFilter.Flag.ANY -> label
            ActivityFilter.Flag.ONLY -> "$label ✓"
            ActivityFilter.Flag.EXCLUDE -> "$label ✗"
        }
    FilterChip(selected = value != ActivityFilter.Flag.ANY, onClick = { onChange(next) }, label = { Text(text) })
}

@Composable
private fun sortLabel(s: ActivitySort): String =
    when (s) {
        ActivitySort.DATE_DESC -> stringResource(Res.string.sort_date_desc)
        ActivitySort.DATE_ASC -> stringResource(Res.string.sort_date_asc)
        ActivitySort.DISTANCE_DESC -> stringResource(Res.string.sort_distance_desc)
        ActivitySort.DURATION_DESC -> stringResource(Res.string.sort_duration_desc)
        ActivitySort.ELEVATION_DESC -> stringResource(Res.string.sort_elevation_desc)
        ActivitySort.SPEED_DESC -> stringResource(Res.string.sort_speed_desc)
    }

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
