package org.opencairn.cairn.core.domain.model

import kotlin.time.Duration
import kotlin.time.Instant

/** A provider-reported lap (split) of an activity. */
data class Lap(
    val index: Int,
    val label: String,
    val startOffset: Duration,
    val elapsed: Duration,
    val moving: Duration,
    val distance: Meters?,
    val avgSpeed: MetersPerSecond?,
    val avgHeartRate: Int?,
    val avgPower: Int?,
    val elevationGain: Meters?,
)

/** One zone with its bounds as fractions of the reference (1.0 = threshold) and the time spent in it. */
data class ZoneBand(
    val label: String,
    val lowPct: Double,
    val highPct: Double,
    val seconds: Int,
)

/** Time in zones against one reference: [basis] is `lthr` or `max` for heart rate, `ftp` for power. */
data class ZoneSummary(
    val basis: String,
    val reference: Double,
    val totalSeconds: Int,
    val bands: List<ZoneBand>,
) {
    /** Share of the total time spent in [band], 0..1. */
    fun share(band: ZoneBand): Float = if (totalSeconds > 0) band.seconds.toFloat() / totalSeconds else 0f
}

/** Cardiac drift of a long steady effort; below 5 % the effort was aerobically coupled. */
data class Decoupling(
    val pct: Double,
    val basis: String,
    val coupled: Boolean,
)

data class ActivityZones(
    val heartRate: ZoneSummary?,
    val power: ZoneSummary?,
    val decoupling: Decoupling?,
) {
    val isEmpty: Boolean get() = heartRate == null && power == null && decoupling == null
}

/** The activity's effort on one segment. */
data class SegmentEffort(
    val segmentId: String,
    val segmentName: String,
    val segmentDistance: Meters,
    /** `4` … `1` or `hc`; null when the segment is not a categorised climb. */
    val climbCategory: String?,
    val elapsed: Duration,
    val avgHeartRate: Int?,
    val avgPower: Int?,
    val isPersonalRecord: Boolean,
    /** 1-based rank among the athlete's own efforts on the segment; 0 when unknown. */
    val personalRank: Int,
)

/** Another activity over the same route (or the reference itself, [isCurrent]). */
data class SimilarActivity(
    val id: String,
    val isCurrent: Boolean,
    val title: String,
    val type: ActivityType,
    val startTime: Instant?,
    val distance: Meters?,
    val moving: Duration,
    val avgHeartRate: Int?,
    val avgPower: Int?,
    /** Share of the reference's segments this activity also covers, 0..1. */
    val overlap: Double,
)

/** Every activity over the reference's route, oldest first, with the reference's standing among them. */
data class SimilarActivities(
    val activities: List<SimilarActivity>,
    val bestMoving: Duration,
    /** 1-based rank of the reference by moving time; 0 when unknown. */
    val currentRank: Int,
    val isBest: Boolean,
) {
    /** Whether there is anything to compare with besides the reference itself. */
    val hasOthers: Boolean get() = activities.any { !it.isCurrent }
}

data class Kudos(
    val count: Int,
    val givenByMe: Boolean,
    val users: List<UserCard>,
)

data class Comment(
    val id: String,
    val body: String,
    val createdAt: Instant?,
    val author: UserCard?,
)

/** What the detail page shows below the summary; a part the server has nothing for stays empty. */
data class ActivityInsights(
    val laps: List<Lap> = emptyList(),
    val zones: ActivityZones? = null,
    val segmentEfforts: List<SegmentEffort> = emptyList(),
    val similar: SimilarActivities? = null,
    val kudos: Kudos? = null,
    val comments: List<Comment> = emptyList(),
)

enum class ExportFormat { GPX, TCX, FIT }

/** A short-lived download link for the merged activity in one file format. */
data class ExportFile(
    val url: String,
    val filename: String,
    val contentType: String,
)
