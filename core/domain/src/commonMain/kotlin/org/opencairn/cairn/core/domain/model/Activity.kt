package org.opencairn.cairn.core.domain.model

import kotlin.time.Duration
import kotlin.time.Instant

/** Sport types as cairn-core defines them (cairn.v1.ActivityType). */
enum class ActivityType(
    val wire: String,
) {
    UNSPECIFIED("unspecified"),
    RIDE("ride"),
    RUN("run"),
    SWIM("swim"),
    HIKE("hike"),
    WALK("walk"),
    ROW("row"),
    SKI("ski"),
    WORKOUT("workout"),
    SNOWBOARD("snowboard"),
    SKATE("skate"),
    KAYAK("kayak"),
    SUP("sup"),
    SURF("surf"),
    GOLF("golf"),
    CLIMB("climb"),
    TENNIS("tennis"),
    ELLIPTICAL("elliptical"),
    WHEELCHAIR("wheelchair"),
    ;

    /** Pace (min/km) is the natural speed unit for these; the rest show km/h. */
    val usesPace: Boolean get() = this == RUN || this == WALK || this == HIKE || this == SWIM

    companion object {
        fun fromWire(s: String): ActivityType = entries.firstOrNull { it.wire == s } ?: UNSPECIFIED
    }
}

enum class Privacy { PRIVATE, FOLLOWERS, PUBLIC }

enum class GearSetBy { UNSPECIFIED, MERGE, USER }

/** Aggregates the server computed; everything optional, everything SI. */
data class ActivitySummary(
    val distance: Meters? = null,
    val elevationGain: Meters? = null,
    val elevationLoss: Meters? = null,
    val minElevation: Meters? = null,
    val maxElevation: Meters? = null,
    val avgSpeed: MetersPerSecond? = null,
    val maxSpeed: MetersPerSecond? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val normalizedPower: Int? = null,
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    val avgTemperatureC: Double? = null,
    val calories: Int? = null,
    val tss: Double? = null,
    val intensityFactor: Double? = null,
    val poolLengthM: Int? = null,
    val totalStrokes: Int? = null,
)

data class ActivitySourceInfo(
    val id: String,
    val provider: String,
    val externalId: String,
    val status: String,
    val importedAt: Instant?,
    val hasStream: Boolean,
)

data class Activity(
    val id: String,
    val type: ActivityType,
    val discipline: String,
    val customSubtype: String,
    val isVirtual: Boolean,
    val isEbike: Boolean,
    val isCommute: Boolean,
    val isRace: Boolean,
    val title: String,
    val description: String,
    val startTime: Instant,
    val endTime: Instant?,
    val elapsed: Duration,
    val moving: Duration,
    val timezone: String,
    val summary: ActivitySummary,
    val tags: List<String>,
    val gearId: String?,
    val gearName: String?,
    val gearSetBy: GearSetBy,
    val gearUnset: Boolean,
    val privacy: Privacy,
    val startPlace: String,
    val startLat: Double?,
    val startLng: Double?,
    val mapImageUrl: String?,
    val mapImageEtag: String,
    val primaryStreamSourceId: String,
    val sources: List<ActivitySourceInfo>,
    val updatedAt: Instant?,
) {
    val displayTitle: String get() = title.ifBlank { "${type.wire.replaceFirstChar { it.uppercase() }} activity" }
}

/** Mirrors cairn.v1.ActivityFilter; every field optional, "any" when unset. */
data class ActivityFilter(
    val types: Set<ActivityType> = emptySet(),
    val disciplines: Set<String> = emptySet(),
    val from: Instant? = null,
    val to: Instant? = null,
    val gearIds: Set<String> = emptySet(),
    val virtual: Flag = Flag.ANY,
    val ebike: Flag = Flag.ANY,
    val commute: Flag = Flag.ANY,
    val race: Flag = Flag.ANY,
    val distanceMinM: Double? = null,
    val distanceMaxM: Double? = null,
    val durationMinS: Double? = null,
    val durationMaxS: Double? = null,
    val elevationMinM: Double? = null,
    val elevationMaxM: Double? = null,
    val speedMinMps: Double? = null,
    val speedMaxMps: Double? = null,
    val hrMinBpm: Double? = null,
    val hrMaxBpm: Double? = null,
    val powerMinW: Double? = null,
    val powerMaxW: Double? = null,
) {
    enum class Flag { ANY, ONLY, EXCLUDE }

    val isEmpty: Boolean get() = this == ActivityFilter()
}

enum class ActivitySort { DATE_DESC, DATE_ASC, DISTANCE_DESC, DURATION_DESC, ELEVATION_DESC, SPEED_DESC }

data class Facet(
    val value: String,
    val count: Int,
)

data class ActivityFacets(
    val types: List<Facet> = emptyList(),
    val disciplines: List<Facet> = emptyList(),
    val years: List<Facet> = emptyList(),
)

/** One cached search result set: what the list screen renders. */
data class ActivitySearchPage(
    val activities: List<Activity>,
    val facets: ActivityFacets,
    val total: Int,
    val matched: Int,
    val hasMore: Boolean,
    val fetchedAt: Instant?,
)

data class Totals(
    val count: Int,
    val distance: Meters,
    val moving: Duration,
    val elevationGain: Meters,
)

data class Overview(
    val totals: Totals,
    val bySport: List<Facet>,
    val recent: List<Activity>,
    val thisWeek: Totals,
    val lastWeek: Totals,
    val thisWeekRange: Pair<Instant, Instant>?,
    val lastWeekRange: Pair<Instant, Instant>?,
)

enum class BestEffortMetric { PACE, SPEED, POWER, HEART_RATE, VAM }

enum class BestEffortWindowKind { DISTANCE, DURATION }

data class BestEffort(
    val id: String,
    val activityId: String,
    val metric: BestEffortMetric,
    val windowKind: BestEffortWindowKind,
    val windowValue: Int,
    val achievedValue: Double,
    val duration: Duration,
    val startOffset: Int,
)

data class Attachment(
    val id: String,
    val originalUrl: String,
    val mediumUrl: String?,
    val thumbUrl: String?,
    val width: Int,
    val height: Int,
    val blurhash: String?,
    val caption: String,
)

/** A value from the cache plus how fresh it is. */
data class Cached<T>(
    val value: T?,
    val fetchedAt: Instant?,
) {
    val isEmpty: Boolean get() = value == null
}
