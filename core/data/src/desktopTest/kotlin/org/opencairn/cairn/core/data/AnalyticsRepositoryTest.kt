package org.opencairn.cairn.core.data

import app.cash.turbine.test
import cairn.v1.EncodedPolyline
import cairn.v1.GetHeatmapRequest
import cairn.v1.GetHeatmapResponse
import cairn.v1.GetStatsResponse
import cairn.v1.GetTrainingLoadRequest
import cairn.v1.GetTrainingLoadResponse
import cairn.v1.HeatmapTrack
import cairn.v1.Totals
import cairn.v1.TrainingLoadDay
import cairn.v1.YearStat
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class AnalyticsRepositoryTest {
    private suspend fun repo(core: FakeCore) =
        AnalyticsRepositoryImpl(core.signIn().scope, core.snapshots, core.dispatchers)

    @Test
    fun trainingLoadIsCachedPerRange() =
        runTest {
            val core = FakeCore()
            val asked = mutableListOf<Int>()
            core.on("StatsService/GetTrainingLoad") { body ->
                val days = GetTrainingLoadRequest.ADAPTER.decode(body).days
                asked += days
                val day = TrainingLoadDay(date = "2026-11-20", ctl = days / 10.0, atl = 50.0, tsb = -8.0, tss = 80.0)
                Reply.Ok(GetTrainingLoadResponse(days = listOf(day), current_ctl = days / 10.0, current_tsb = -8.0))
            }
            val repo = repo(core)
            repo.refreshTrainingLoad(TrainingLoadRange.THREE_MONTHS).getOrThrow()
            repo.refreshTrainingLoad(TrainingLoadRange.ONE_YEAR).getOrThrow()
            assertEquals(listOf(90, 365), asked)
            repo.observeTrainingLoad(TrainingLoadRange.ONE_YEAR).test {
                val load = awaitItem().value!!
                assertEquals(36.5, load.fitness)
                assertEquals(-8.0, load.form)
                assertEquals("2026-11-20", load.days.single().date)
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeTrainingLoad(TrainingLoadRange.ALL).test {
                assertTrue(awaitItem().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun statsRoundTrip() =
        runTest {
            val core = FakeCore()
            core.on("StatsService/GetStats") {
                val year = YearStat(year = 2026, count = 12, distance_m = 500_000.0, moving_s = 7200.0)
                Reply.Ok(
                    GetStatsResponse(
                        totals = Totals(count = 40),
                        by_year = listOf(year),
                        longest_distance = cairn.v1.Activity(id = "a9", title = "Epic"),
                    ),
                )
            }
            val repo = repo(core)
            repo.refreshStats().getOrThrow()
            repo.observeStats().test {
                val stats = awaitItem().value!!
                assertEquals(40, stats.totals.count)
                assertEquals(2.hours, stats.byYear.single().moving)
                assertEquals(
                    500.0,
                    stats.byYear
                        .single()
                        .distance.kilometers,
                )
                assertEquals("Epic", stats.longestDistance?.title)
                assertEquals(null, stats.longestDuration)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun heatmapDecodesTracksAndDropsEmptyOnes() =
        runTest {
            val core = FakeCore()
            var filter: cairn.v1.ActivityFilter? = null
            core.on("StatsService/GetHeatmap") { body ->
                filter = GetHeatmapRequest.ADAPTER.decode(body).filter
                val ride = cairn.v1.ActivityType.ACTIVITY_TYPE_RIDE
                val tracks =
                    listOf(
                        HeatmapTrack("a1", ride, polyline = EncodedPolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)),
                        HeatmapTrack("a2", ride, polyline = EncodedPolyline("_p~iF~ps|U", 5)),
                        HeatmapTrack("a3", ride),
                    )
                Reply.Ok(GetHeatmapResponse(matched = 3, with_track = 2, truncated = true, tracks = tracks))
            }
            val heatmap = repo(core).heatmap(ActivityFilter(types = setOf(ActivityType.RIDE))).getOrThrow()
            assertEquals(listOf("a1"), heatmap.tracks.map { it.activityId })
            assertEquals(
                3,
                heatmap.tracks
                    .single()
                    .points.size,
            )
            assertTrue(heatmap.truncated)
            assertEquals(listOf(cairn.v1.ActivityType.ACTIVITY_TYPE_RIDE), filter?.types)
        }
}
