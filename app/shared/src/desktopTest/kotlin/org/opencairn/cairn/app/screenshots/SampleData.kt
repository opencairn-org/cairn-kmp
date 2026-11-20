package org.opencairn.cairn.app.screenshots

import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFacets
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySummary
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.ActivityZones
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.Comment
import org.opencairn.cairn.core.domain.model.Decoupling
import org.opencairn.cairn.core.domain.model.Facet
import org.opencairn.cairn.core.domain.model.GearSetBy
import org.opencairn.cairn.core.domain.model.Kudos
import org.opencairn.cairn.core.domain.model.Lap
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.Privacy
import org.opencairn.cairn.core.domain.model.SegmentEffort
import org.opencairn.cairn.core.domain.model.SimilarActivities
import org.opencairn.cairn.core.domain.model.SimilarActivity
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.model.TrainingLoad
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.core.domain.model.UserCard
import org.opencairn.cairn.core.domain.model.YearStat
import org.opencairn.cairn.core.domain.model.ZoneBand
import org.opencairn.cairn.core.domain.model.ZoneSummary
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** "Now" of every screenshot: 2026-11-20 12:00 UTC, so relative dates never move. */
val NOW: Instant = Instant.fromEpochSeconds(1_795_176_000)

val fixedClock =
    object : Clock {
        override fun now(): Instant = NOW
    }

val prefs = Preferences()
val formatters = Formatters(prefs.units)
val dates = DateFormatters(prefs, fixedClock)

private val titles =
    listOf("Morning ride", "Lunch run", "Evening gravel loop", "Recovery spin", "Long run", "Hill repeats", "Commute")

fun sampleActivity(
    index: Int,
    type: ActivityType = if (index % 3 == 1) ActivityType.RUN else ActivityType.RIDE,
): Activity {
    val distance = if (type == ActivityType.RUN) 8_400.0 + index * 650 else 42_300.0 + index * 3_100
    val moving = if (type == ActivityType.RUN) 44.minutes + (index * 3).minutes else 95.minutes + (index * 7).minutes
    return Activity(
        id = "a$index",
        type = type,
        discipline = "",
        customSubtype = "",
        isVirtual = false,
        isEbike = false,
        isCommute = index % 7 == 6,
        isRace = index == 4,
        title = titles[index % titles.size],
        description = if (index == 0) "Tailwind out, headwind home. Legs felt good on the climb." else "",
        startTime = NOW - index.days - 5.minutes * index,
        endTime = null,
        elapsed = moving + 6.minutes,
        moving = moving,
        timezone = "Europe/Berlin",
        summary =
            ActivitySummary(
                distance = Meters(distance),
                elevationGain = Meters(180.0 + index * 45),
                elevationLoss = Meters(175.0 + index * 45),
                maxElevation = Meters(612.0),
                avgSpeed = MetersPerSecond(distance / moving.inWholeSeconds),
                maxSpeed = MetersPerSecond(distance / moving.inWholeSeconds * 1.9),
                avgHeartRate = 142 + index,
                maxHeartRate = 171 + index,
                avgPower = if (type == ActivityType.RIDE) 198 + index * 3 else null,
                normalizedPower = if (type == ActivityType.RIDE) 214 + index * 3 else null,
                avgCadence = 86,
                calories = 980 + index * 40,
                tss = 74.0 + index,
            ),
        tags = emptyList(),
        gearId = "g1",
        gearName = if (type == ActivityType.RIDE) "Canyon Ultimate" else "Saucony Ride",
        gearSetBy = GearSetBy.MERGE,
        gearUnset = false,
        privacy = Privacy.PRIVATE,
        startPlace = "Freiburg im Breisgau",
        startLat = null,
        startLng = null,
        mapImageUrl = null,
        mapImageEtag = "",
        primaryStreamSourceId = "",
        sources = emptyList(),
        updatedAt = null,
    )
}

val sampleActivities: List<Activity> = List(14, ::sampleActivity)

val samplePage =
    ActivitySearchPage(
        activities = sampleActivities,
        facets =
            ActivityFacets(
                types = listOf(Facet("ride", 212), Facet("run", 97)),
                years = listOf(Facet("2026", 141), Facet("2025", 168)),
            ),
        total = 309,
        matched = 309,
        hasMore = true,
        fetchedAt = NOW,
    )

val sampleBestEfforts =
    listOf(
        BestEffort("e1", "a0", BestEffortMetric.POWER, BestEffortWindowKind.DURATION, 300, 312.0, 300.seconds, 1200),
        BestEffort("e2", "a0", BestEffortMetric.POWER, BestEffortWindowKind.DURATION, 1200, 268.0, 1200.seconds, 900),
        BestEffort("e3", "a0", BestEffortMetric.SPEED, BestEffortWindowKind.DURATION, 3600, 8.9, 3600.seconds, 300),
    )

private fun lap(
    index: Int,
    minutes: Int,
    heartRate: Int,
    power: Int,
) = Lap(
    index = index,
    label = "",
    startOffset = Duration.ZERO,
    elapsed = minutes.minutes + 12.seconds,
    moving = minutes.minutes,
    distance = Meters(minutes * (420.0 + power)),
    avgSpeed = MetersPerSecond((420.0 + power) / 60),
    avgHeartRate = heartRate,
    avgPower = power,
    elevationGain = Meters(40.0),
)

private val ann = UserCard("u2", "ann", "Ann Kessler", null, remote = false)
private val ben = UserCard("u3", "ben", "Ben Moreau", null, remote = false)

val sampleInsights =
    ActivityInsights(
        laps = listOf(lap(1, 22, 138, 185), lap(2, 31, 151, 224), lap(3, 26, 147, 206), lap(4, 16, 133, 168)),
        zones =
            ActivityZones(
                heartRate =
                    ZoneSummary(
                        basis = "lthr",
                        reference = 165.0,
                        totalSeconds = 5700,
                        bands =
                            listOf(
                                ZoneBand("Z1", 0.0, 0.81, 760),
                                ZoneBand("Z2", 0.81, 0.9, 2650),
                                ZoneBand("Z3", 0.9, 0.94, 1480),
                                ZoneBand("Z4", 0.94, 1.0, 640),
                                ZoneBand("Z5", 1.0, 1.1, 170),
                            ),
                    ),
                power = null,
                decoupling = Decoupling(3.4, "power", coupled = true),
            ),
        segmentEfforts =
            listOf(
                SegmentEffort(
                    "s1",
                    "Schauinsland from Günterstal",
                    Meters(11_600.0),
                    "1",
                    2714.seconds,
                    158,
                    246,
                    true,
                    1,
                ),
                SegmentEffort("s2", "Dreisam towpath", Meters(4_200.0), null, 486.seconds, 141, 201, false, 4),
            ),
        similar =
            SimilarActivities(
                activities =
                    listOf(
                        SimilarActivity(
                            "a40",
                            false,
                            "Schauinsland loop",
                            ActivityType.RIDE,
                            NOW - 61.days,
                            null,
                            104.minutes,
                            149,
                            188,
                            0.9,
                        ),
                        SimilarActivity(
                            "a21",
                            false,
                            "Sunday climb",
                            ActivityType.RIDE,
                            NOW - 19.days,
                            null,
                            93.minutes,
                            150,
                            203,
                            1.0,
                        ),
                        SimilarActivity(
                            "a0",
                            true,
                            "Morning ride",
                            ActivityType.RIDE,
                            NOW,
                            null,
                            95.minutes,
                            142,
                            198,
                            1.0,
                        ),
                    ),
                bestMoving = 93.minutes,
                currentRank = 2,
                isBest = false,
            ),
        kudos = Kudos(5, givenByMe = false, users = listOf(ann, ben)),
        comments =
            listOf(
                Comment("c1", "That climb in under 46 minutes, chapeau!", NOW - 2.days, ann),
                Comment("c2", "Next time I am coming along.", NOW - 1.days, ben),
            ),
    )

/** Half a year of training load: fitness climbing slowly, fatigue swinging around it week by week. */
val sampleTrainingLoad: TrainingLoad =
    run {
        val days =
            List(180) { i ->
                val fitness = 48.0 + i * 0.09 + 3 * sin(i / 23.0)
                val fatigue = fitness + 14 * sin(i / 3.4) + 4 * sin(i / 11.0)
                val date = (NOW - (179 - i).days).toString().take(10)
                TrainingLoadDay(date, fitness, fatigue, fitness - fatigue, 60.0 + 40 * sin(i / 2.0))
            }
        val last = days.last()
        TrainingLoad(days, last.fitness, last.fatigue, last.form)
    }

val sampleStats =
    Stats(
        totals = Totals(309, Meters(11_840_000.0), 512.hours, Meters(142_300.0)),
        bySport = listOf(Facet("ride", 212), Facet("run", 97)),
        byDiscipline = emptyList(),
        byYear =
            listOf(
                YearStat(2024, 61, Meters(2_310_000.0), 101.hours, Meters(27_400.0)),
                YearStat(2025, 107, Meters(4_020_000.0), 176.hours, Meters(49_800.0)),
                YearStat(2026, 141, Meters(5_510_000.0), 235.hours, Meters(65_100.0)),
            ),
        longestDistance = sampleActivity(12).copy(title = "Black Forest traverse"),
        longestDuration = sampleActivity(11).copy(title = "Feldberg and back"),
    )
