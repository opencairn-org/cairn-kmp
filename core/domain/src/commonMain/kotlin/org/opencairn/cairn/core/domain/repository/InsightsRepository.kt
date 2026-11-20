package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.ExportFile
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.Heatmap
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.TrainingLoad
import org.opencairn.cairn.core.domain.model.TrainingLoadRange

/** The parts of the activity detail page below the summary: laps, zones, segments, route history, social. */
interface ActivityInsightsRepository {
    /** From the cache; parts never fetched (or that the activity does not have) are empty. */
    fun observeInsights(activityId: String): Flow<ActivityInsights>

    /** Fetches every part; one the server does not offer or share is skipped, the rest still land. */
    suspend fun refreshInsights(activityId: String): Result<Unit>

    /** Asks the server for a download link of the activity in [format]; nothing is cached. */
    suspend fun export(
        activityId: String,
        format: ExportFormat,
    ): Result<ExportFile>
}

/** Aggregates over all activities: training load, lifetime statistics and the heatmap. */
interface AnalyticsRepository {
    fun observeTrainingLoad(range: TrainingLoadRange): Flow<Cached<TrainingLoad>>

    suspend fun refreshTrainingLoad(range: TrainingLoadRange): Result<Unit>

    fun observeStats(): Flow<Cached<Stats>>

    suspend fun refreshStats(): Result<Unit>

    /** Not cached: the tracks of a few thousand activities are large and the map needs the network anyway. */
    suspend fun heatmap(filter: ActivityFilter): Result<Heatmap>
}
