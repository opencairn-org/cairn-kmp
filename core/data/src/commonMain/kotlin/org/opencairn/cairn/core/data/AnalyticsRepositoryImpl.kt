package org.opencairn.cairn.core.data

import cairn.v1.GetHeatmapRequest
import cairn.v1.GetHeatmapResponse
import cairn.v1.GetStatsRequest
import cairn.v1.GetStatsResponse
import cairn.v1.GetTrainingLoadRequest
import cairn.v1.GetTrainingLoadResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Facet
import org.opencairn.cairn.core.domain.model.Heatmap
import org.opencairn.cairn.core.domain.model.HeatmapTrack
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.TrainingLoad
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import org.opencairn.cairn.core.domain.model.YearStat
import org.opencairn.cairn.core.domain.model.decodePolyline
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Duration.Companion.seconds

class AnalyticsRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
    private val dispatchers: DispatcherProvider,
) : AnalyticsRepository {
    override fun observeTrainingLoad(range: TrainingLoadRange): Flow<Cached<TrainingLoad>> =
        snapshots.observe(loadKey(range)) { GetTrainingLoadResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshTrainingLoad(range: TrainingLoadRange): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .stats
                    .GetTrainingLoad()
                    .execute(GetTrainingLoadRequest(days = range.days))
            snapshots.put(loadKey(range), response.encode())
        }

    override fun observeStats(): Flow<Cached<Stats>> =
        snapshots.observe(STATS) { GetStatsResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshStats(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .stats
                    .GetStats()
                    .execute(GetStatsRequest())
            snapshots.put(STATS, response.encode())
        }

    override suspend fun heatmap(filter: ActivityFilter): Result<Heatmap> =
        apiResult {
            val response =
                scope
                    .api()
                    .stats
                    .GetHeatmap()
                    .execute(GetHeatmapRequest(filter = filter.toProto()))
            // A few thousand polylines: decode off the caller's thread.
            withContext(dispatchers.default) { response.toDomain() }
        }

    private fun loadKey(range: TrainingLoadRange) = "training-load:${range.name}"

    private companion object {
        const val STATS = "stats"
    }
}

internal fun GetTrainingLoadResponse.toDomain() =
    TrainingLoad(
        days = days.map { TrainingLoadDay(it.date, it.ctl, it.atl, it.tsb, it.tss) },
        fitness = current_ctl,
        fatigue = current_atl,
        form = current_tsb,
    )

internal fun GetStatsResponse.toDomain() =
    Stats(
        totals = totals.toDomain(),
        bySport = by_sport.map { Facet(it.value_, it.count) },
        byDiscipline = by_discipline.map { Facet(it.value_, it.count) },
        byYear =
            by_year.map {
                YearStat(it.year, it.count, Meters(it.distance_m), it.moving_s.seconds, Meters(it.elevation_gain_m))
            },
        longestDistance = longest_distance?.toDomain(),
        longestDuration = longest_duration?.toDomain(),
    )

internal fun GetHeatmapResponse.toDomain() =
    Heatmap(
        matched = matched,
        withTrack = with_track,
        truncated = truncated,
        facets = (facets ?: cairn.v1.ActivityFacets()).toDomain(),
        tracks =
            tracks.mapNotNull { t ->
                val line = t.polyline ?: return@mapNotNull null
                val points = decodePolyline(line.encoded, line.precision.takeIf { it > 0 } ?: 5)
                if (points.size < 2) null else HeatmapTrack(t.activity_id, t.type.toDomain(), points)
            },
    )
