package org.opencairn.cairn.feature.analysis

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityFacets
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Facet
import org.opencairn.cairn.core.domain.model.GeoPoint
import org.opencairn.cairn.core.domain.model.Heatmap
import org.opencairn.cairn.core.domain.model.HeatmapTrack
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.model.TrainingLoad
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import org.opencairn.cairn.core.domain.model.YearStat
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun day(
    date: String,
    fitness: Double,
) = TrainingLoadDay(date, fitness, fitness + 10, -10.0, 60.0)

private class FakeAnalytics : AnalyticsRepository {
    val loads = MutableStateFlow<Map<TrainingLoadRange, TrainingLoad>>(emptyMap())
    val stats = MutableStateFlow<Stats?>(null)
    val refreshedRanges = mutableListOf<TrainingLoadRange>()
    val heatmapFilters = mutableListOf<ActivityFilter>()
    var fail: Throwable? = null

    private fun outcome(): Result<Unit> = fail?.let { Result.failure(it) } ?: Result.success(Unit)

    override fun observeTrainingLoad(range: TrainingLoadRange): Flow<Cached<TrainingLoad>> =
        loads.map { Cached(it[range], Instant.fromEpochSeconds(1)) }

    override suspend fun refreshTrainingLoad(range: TrainingLoadRange): Result<Unit> {
        refreshedRanges += range
        return outcome()
    }

    override fun observeStats(): Flow<Cached<Stats>> = stats.map { Cached(it, Instant.fromEpochSeconds(1)) }

    override suspend fun refreshStats(): Result<Unit> = outcome()

    override suspend fun heatmap(filter: ActivityFilter): Result<Heatmap> {
        heatmapFilters += filter
        fail?.let { return Result.failure(it) }
        val track = HeatmapTrack("a1", ActivityType.RIDE, listOf(GeoPoint(48.0, 7.8), GeoPoint(48.1, 7.9)))
        val facets = ActivityFacets(types = listOf(Facet("run", 4), Facet("ride", 9), Facet("mystery", 1)))
        return Result.success(Heatmap(matched = 14, withTrack = 12, truncated = false, facets, listOf(track)))
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitItemWith(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

class AnalysisViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeAnalytics()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun trainingLoadFollowsTheRangeAndTheCrosshair() =
        runTest(dispatcher) {
            val halfYear =
                TrainingLoad(
                    listOf(day("2026-11-18", 60.0), day("2026-11-19", 61.0), day("2026-11-20", 62.0)),
                    62.0,
                    72.0,
                    -10.0,
                )
            repo.loads.value = mapOf(TrainingLoadRange.SIX_MONTHS to halfYear)
            val vm = AnalysisViewModel(repo)
            vm.state.test {
                val shown = awaitItemWith { it.hasData && !it.refreshing }
                // Without a crosshair the tiles describe the latest day.
                assertEquals("2026-11-20", shown.shownDay?.date)
                vm.select(0)
                assertEquals("2026-11-18", awaitItemWith { it.selected == 0 }.shownDay?.date)
                vm.setRange(TrainingLoadRange.ONE_YEAR)
                val year = awaitItemWith { it.range == TrainingLoadRange.ONE_YEAR && !it.refreshing }
                assertNull(year.selected)
                // Nothing cached for the year: the empty note, not a chart of one point.
                assertFalse(year.hasData)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(TrainingLoadRange.SIX_MONTHS, TrainingLoadRange.ONE_YEAR), repo.refreshedRanges)
        }

    @Test
    fun trainingLoadReportsAFailedRefresh() =
        runTest(dispatcher) {
            repo.fail = AppError.Network()
            val vm = AnalysisViewModel(repo)
            vm.state.test {
                assertEquals("Offline, showing cached data", awaitItemWith { it.error != null }.error)
                // Choosing the range that is already shown asks the server nothing.
                vm.setRange(TrainingLoadRange.SIX_MONTHS)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("+7", signed(7))
            assertEquals("-12", signed(-12))
            assertEquals("0", signed(0))
        }

    @Test
    fun statsListYearsNewestFirstAndScaleToTheFarthest() =
        runTest(dispatcher) {
            val years =
                listOf(
                    YearStat(2024, 80, Meters(3_000_000.0), 150.hours, Meters(30_000.0)),
                    YearStat(2026, 120, Meters(5_200_000.0), 240.hours, Meters(61_000.0)),
                    YearStat(2025, 100, Meters(4_100_000.0), 200.hours, Meters(48_000.0)),
                )
            val totals = Totals(300, Meters(12_300_000.0), 590.hours, Meters(139_000.0))
            repo.stats.value = Stats(totals, emptyList(), emptyList(), years, null, null)
            val vm = StatsViewModel(repo)
            vm.state.test {
                val st = awaitItemWith { it.stats.value != null && !it.refreshing }
                assertEquals(listOf(2026, 2025, 2024), st.years.map { it.year })
                assertEquals(5_200_000.0, st.longestYearMeters)
                assertNull(st.error)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0.0, StatsUiState().longestYearMeters)
        }

    @Test
    fun heatmapKeepsTheSportsOfTheFullAnswerWhileFiltering() =
        runTest(dispatcher) {
            val vm = HeatmapViewModel(repo)
            vm.state.test {
                val all = awaitItemWith { it.heatmap != null && !it.loading }
                // Most frequent first; a sport this app does not know is left out.
                assertEquals(listOf(ActivityType.RIDE, ActivityType.RUN), all.sports)
                assertEquals(12, all.heatmap?.withTrack)
                vm.setType(ActivityType.RUN)
                val runs = awaitItemWith { it.type == ActivityType.RUN && !it.loading }
                assertEquals(listOf(ActivityType.RIDE, ActivityType.RUN), runs.sports)
                // A failed reload keeps the map that is on screen.
                repo.fail = AppError.Unavailable()
                vm.setType(null)
                val failed = awaitItemWith { it.error != null }
                assertTrue(failed.heatmap != null)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(emptySet(), setOf(ActivityType.RUN), emptySet()),
                repo.heatmapFilters.map { it.types },
            )
        }
}
