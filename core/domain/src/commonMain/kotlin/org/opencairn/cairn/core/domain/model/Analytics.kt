package org.opencairn.cairn.core.domain.model

import kotlin.time.Duration

/** One day of the training-load model: fitness (CTL), fatigue (ATL), form (TSB) and the day's stress. */
data class TrainingLoadDay(
    /** ISO `YYYY-MM-DD`, UTC. */
    val date: String,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
    val stress: Double,
)

data class TrainingLoad(
    val days: List<TrainingLoadDay>,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
)

/** How far back the training-load chart looks. */
enum class TrainingLoadRange(
    val days: Int,
) {
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365),
    ALL(3650),
}

data class YearStat(
    val year: Int,
    val count: Int,
    val distance: Meters,
    val moving: Duration,
    val elevationGain: Meters,
)

/** Lifetime statistics: totals, the split by sport and discipline, one row per year, the longest activities. */
data class Stats(
    val totals: Totals,
    val bySport: List<Facet>,
    val byDiscipline: List<Facet>,
    val byYear: List<YearStat>,
    val longestDistance: Activity?,
    val longestDuration: Activity?,
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class HeatmapTrack(
    val activityId: String,
    val type: ActivityType,
    val points: List<GeoPoint>,
)

/** Every GPS track matching a filter, for the combined map. */
data class Heatmap(
    val matched: Int,
    val withTrack: Int,
    /** The server considered only the newest activities; more match. */
    val truncated: Boolean,
    val facets: ActivityFacets,
    val tracks: List<HeatmapTrack>,
)

/**
 * Decodes an encoded polyline (the Google algorithm) with [precision] decimal digits: 5 is the
 * common form, 6 the high-precision one. Malformed input yields the points decoded so far.
 */
fun decodePolyline(
    encoded: String,
    precision: Int = 5,
): List<GeoPoint> {
    var factor = 1.0
    repeat(precision) { factor *= 10 }
    val points = ArrayList<GeoPoint>(encoded.length / 4)
    var index = 0
    var lat = 0L
    var lng = 0L
    // Each point is a latitude delta followed by a longitude delta.
    var pair = encoded.nextPair(index)
    while (pair != null) {
        lat += pair.dLat
        lng += pair.dLng
        index = pair.next
        points += GeoPoint(lat / factor, lng / factor)
        pair = encoded.nextPair(index)
    }
    return points
}

private class DeltaPair(
    val dLat: Long,
    val dLng: Long,
    val next: Int,
)

private fun String.nextPair(start: Int): DeltaPair? {
    val (dLat, afterLat) = nextDelta(start) ?: return null
    val (dLng, afterLng) = nextDelta(afterLat) ?: return null
    return DeltaPair(dLat, dLng, afterLng)
}

/** One zig-zag varint starting at [start]: the signed delta and the index after it; null when cut short. */
private fun String.nextDelta(start: Int): Pair<Long, Int>? {
    var index = start
    var shift = 0
    var result = 0L
    while (index < length) {
        val chunk = this[index++].code - 63
        if (chunk < 0) return null
        result = result or ((chunk and 0x1f).toLong() shl shift)
        shift += 5
        if (chunk < 0x20) {
            val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            return delta to index
        }
    }
    return null
}
