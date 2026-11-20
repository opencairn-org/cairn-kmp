package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ActivityZones
import org.opencairn.cairn.core.domain.model.Comment
import org.opencairn.cairn.core.domain.model.Decoupling
import org.opencairn.cairn.core.domain.model.Kudos
import org.opencairn.cairn.core.domain.model.Lap
import org.opencairn.cairn.core.domain.model.SegmentEffort
import org.opencairn.cairn.core.domain.model.SimilarActivities
import org.opencairn.cairn.core.domain.model.SimilarActivity
import org.opencairn.cairn.core.domain.model.ZoneSummary
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.decoupling_basis_pace
import org.opencairn.cairn.ui.design.resources.decoupling_basis_power
import org.opencairn.cairn.ui.design.resources.detail_climb_category
import org.opencairn.cairn.ui.design.resources.detail_comments
import org.opencairn.cairn.ui.design.resources.detail_decoupling
import org.opencairn.cairn.ui.design.resources.detail_decoupling_coupled
import org.opencairn.cairn.ui.design.resources.detail_decoupling_drift
import org.opencairn.cairn.ui.design.resources.detail_kudos
import org.opencairn.cairn.ui.design.resources.detail_lap_distance
import org.opencairn.cairn.ui.design.resources.detail_lap_hr
import org.opencairn.cairn.ui.design.resources.detail_lap_pace
import org.opencairn.cairn.ui.design.resources.detail_lap_power
import org.opencairn.cairn.ui.design.resources.detail_lap_speed
import org.opencairn.cairn.ui.design.resources.detail_lap_time
import org.opencairn.cairn.ui.design.resources.detail_laps
import org.opencairn.cairn.ui.design.resources.detail_segment_pr
import org.opencairn.cairn.ui.design.resources.detail_segments
import org.opencairn.cairn.ui.design.resources.detail_similar
import org.opencairn.cairn.ui.design.resources.detail_similar_best
import org.opencairn.cairn.ui.design.resources.detail_similar_rank
import org.opencairn.cairn.ui.design.resources.detail_similar_this
import org.opencairn.cairn.ui.design.resources.detail_zones_hr
import org.opencairn.cairn.ui.design.resources.detail_zones_power
import org.opencairn.cairn.ui.design.resources.zones_basis_ftp
import org.opencairn.cairn.ui.design.resources.zones_basis_lthr
import org.opencairn.cairn.ui.design.resources.zones_basis_max
import org.opencairn.cairn.ui.design.theme.CairnColors
import kotlin.time.Duration.Companion.seconds

/** How many other activities over the same route the page lists before it stops. */
private const val SIMILAR_LIMIT = 8

/** Easy to all-out, like the web's zone colours. */
private val zoneColors =
    listOf(
        CairnColors.zinc400,
        Color(0xFF60A5FA),
        CairnColors.green400,
        CairnColors.amber400,
        Color(0xFFFB923C),
        CairnColors.red400,
        Color(0xFFC084FC),
    )

/** The sections below the summary: laps, zones, segments, the route's history, kudos and comments. */
internal fun LazyListScope.insightSections(
    insights: ActivityInsights,
    usesPace: Boolean,
    formatters: Formatters,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    if (insights.laps.size > 1) {
        item(key = "laps") { LapsTable(insights.laps, usesPace, formatters) }
    }
    insights.zones?.let { zones -> item(key = "zones") { Zones(zones, formatters) } }
    if (insights.segmentEfforts.isNotEmpty()) {
        item(key = "segments") {
            Column {
                Text(stringResource(Res.string.detail_segments), style = MaterialTheme.typography.titleMedium)
                insights.segmentEfforts.forEach { SegmentRow(it, formatters) }
            }
        }
    }
    insights.similar?.let { similar ->
        item(key = "similar") { SimilarRoutes(similar, formatters, dates, onOpenActivity) }
    }
    val kudos = insights.kudos?.takeIf { it.count > 0 }
    if (kudos != null || insights.comments.isNotEmpty()) {
        item(key = "social") { Social(kudos, insights.comments, dates) }
    }
}

@Composable
internal fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun LapsTable(
    laps: List<Lap>,
    usesPace: Boolean,
    f: Formatters,
) {
    val showHr = laps.any { it.avgHeartRate != null }
    val showPower = laps.any { it.avgPower != null }
    Column {
        SectionTitle(Res.string.detail_laps)
        LapRow(
            index = "#",
            distance = stringResource(Res.string.detail_lap_distance),
            time = stringResource(Res.string.detail_lap_time),
            speed = stringResource(if (usesPace) Res.string.detail_lap_pace else Res.string.detail_lap_speed),
            hr = stringResource(Res.string.detail_lap_hr).takeIf { showHr },
            power = stringResource(Res.string.detail_lap_power).takeIf { showPower },
            header = true,
        )
        HorizontalDivider()
        laps.forEach { lap ->
            val moving = lap.moving.takeIf { it.inWholeSeconds > 0 } ?: lap.elapsed
            LapRow(
                index = lap.index.toString(),
                distance = lap.distance?.let(f::distance) ?: "–",
                time = f.clock(moving),
                speed = lap.avgSpeed?.let { if (usesPace) f.pace(it) else f.speed(it) } ?: "–",
                hr = (lap.avgHeartRate?.toString() ?: "–").takeIf { showHr },
                power = (lap.avgPower?.toString() ?: "–").takeIf { showPower },
                header = false,
            )
        }
    }
}

/** One table line; a null [hr] or [power] drops that column for the whole table. */
@Composable
private fun LapRow(
    index: String,
    distance: String,
    time: String,
    speed: String,
    hr: String?,
    power: String?,
    header: Boolean,
) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        listOfNotNull(distance, time, speed, hr, power).forEach { cell ->
            Text(
                cell,
                style = style,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Zones(
    zones: ActivityZones,
    f: Formatters,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        zones.heartRate?.let { ZoneBars(Res.string.detail_zones_hr, it, f) }
        zones.power?.let { ZoneBars(Res.string.detail_zones_power, it, f) }
        zones.decoupling?.let { DecouplingLine(it) }
    }
}

@Composable
private fun ZoneBars(
    title: StringResource,
    summary: ZoneSummary,
    f: Formatters,
) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        val reference = summary.reference.toInt().toString()
        val basis =
            when (summary.basis) {
                "ftp" -> stringResource(Res.string.zones_basis_ftp, reference)
                "max" -> stringResource(Res.string.zones_basis_max, reference)
                else -> stringResource(Res.string.zones_basis_lthr, reference)
            }
        Text(
            basis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        summary.bands.forEachIndexed { i, band ->
            val share = summary.share(band)
            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(band.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(share.coerceIn(0f, 1f))
                                .background(zoneColors[i.coerceAtMost(zoneColors.lastIndex)]),
                    )
                }
                Text(
                    f.clock(band.seconds.seconds),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    f.percent(share),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}

@Composable
private fun DecouplingLine(d: Decoupling) {
    val basis =
        stringResource(if (d.basis == "pace") Res.string.decoupling_basis_pace else Res.string.decoupling_basis_power)
    val pct = ((d.pct * 10).toInt() / 10.0).toString()
    Column {
        Text(stringResource(Res.string.detail_decoupling), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                if (d.coupled) Res.string.detail_decoupling_coupled else Res.string.detail_decoupling_drift,
                pct,
                basis,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (d.coupled) MaterialTheme.colorScheme.onSurface else CairnColors.amber400,
        )
    }
}

@Composable
private fun SegmentRow(
    effort: SegmentEffort,
    f: Formatters,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    effort.segmentName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val category =
                    effort.climbCategory?.let {
                        stringResource(
                            Res.string.detail_climb_category,
                            it.uppercase(),
                        )
                    }
                val details =
                    listOfNotNull(
                        f.distance(effort.segmentDistance),
                        category,
                        effort.avgHeartRate?.let { "$it bpm" },
                        effort.avgPower?.let { "$it W" },
                    )
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.clock(effort.elapsed), style = MaterialTheme.typography.bodyMedium)
                if (effort.isPersonalRecord) {
                    Text(
                        stringResource(Res.string.detail_segment_pr),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (effort.personalRank > 0) {
                    Text(
                        "#${effort.personalRank}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SimilarRoutes(
    similar: SimilarActivities,
    f: Formatters,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    Column {
        SectionTitle(Res.string.detail_similar)
        val count = similar.activities.size
        val standing =
            if (similar.isBest) {
                stringResource(Res.string.detail_similar_best, count)
            } else {
                stringResource(Res.string.detail_similar_rank, similar.currentRank, count, f.clock(similar.bestMoving))
            }
        Text(
            standing,
            style = MaterialTheme.typography.bodyMedium,
            color = if (similar.isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Newest first, the activity on screen always among them.
        val newest = similar.activities.asReversed()
        val shown =
            newest.take(SIMILAR_LIMIT).let { top ->
                if (top.any { it.isCurrent }) {
                    top
                } else {
                    top +
                        newest.first { it.isCurrent }
                }
            }
        shown.forEach { SimilarRow(it, f, dates, onOpenActivity) }
    }
}

@Composable
private fun SimilarRow(
    a: SimilarActivity,
    f: Formatters,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    val open = if (a.isCurrent) Modifier else Modifier.clickable { onOpenActivity(a.id) }
    Row(modifier = open.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (a.isCurrent) stringResource(Res.string.detail_similar_this) else a.title.ifBlank { a.type.wire },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (a.isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                listOfNotNull(
                    a.startTime?.let(dates::date),
                    a.avgHeartRate?.let { "$it bpm" },
                    a.avgPower?.let { "$it W" },
                )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(f.clock(a.moving), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}

@Composable
private fun Social(
    kudos: Kudos?,
    comments: List<Comment>,
    dates: DateFormatters,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kudos?.let {
            val names = it.users.joinToString(", ") { u -> u.displayName }
            Text(stringResource(Res.string.detail_kudos, it.count), style = MaterialTheme.typography.titleMedium)
            if (names.isNotBlank()) {
                Text(
                    names,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (comments.isNotEmpty()) {
            Text(stringResource(Res.string.detail_comments), style = MaterialTheme.typography.titleMedium)
            comments.forEach { c ->
                Column {
                    val meta = listOfNotNull(c.author?.displayName, c.createdAt?.let(dates::relative))
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
