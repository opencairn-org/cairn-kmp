package org.opencairn.cairn.app.screenshots

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.ExportFile
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.repository.ActivityInsightsRepository
import org.opencairn.cairn.core.domain.repository.ActivityRepository

/** Wraps a value the way a warm cache would hand it out. */
fun <T> cached(value: T): Flow<Cached<T>> = flowOf(Cached(value, NOW))

private val done = Result.success(Unit)

/** A cache that already holds everything; refreshes succeed and change nothing. */
class SampleActivities : ActivityRepository {
    override fun observeSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Flow<ActivitySearchPage?> = flowOf(samplePage)

    override suspend fun refreshSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ) = done

    override suspend fun loadMoreSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ) = Result.success(false)

    override fun observeActivity(id: String): Flow<Cached<Activity>> = cached(sampleActivities.first { it.id == id })

    override suspend fun refreshActivity(id: String) = done

    override fun observeBestEfforts(activityId: String): Flow<Cached<List<BestEffort>>> = cached(sampleBestEfforts)

    override suspend fun refreshBestEfforts(activityId: String) = done

    override fun observeAttachments(activityId: String): Flow<Cached<List<Attachment>>> = cached(emptyList())

    override suspend fun refreshAttachments(activityId: String) = done

    override fun observeStream(sourceId: String): Flow<Cached<ActivityStream>> = flowOf(Cached(null, null))

    override suspend fun refreshStream(sourceId: String) = done
}

class SampleInsights : ActivityInsightsRepository {
    override fun observeInsights(activityId: String): Flow<ActivityInsights> = flowOf(sampleInsights)

    override suspend fun refreshInsights(activityId: String) = done

    override suspend fun export(
        activityId: String,
        format: ExportFormat,
    ): Result<ExportFile> =
        Result.success(ExportFile("https://cairn.example/export", "ride.gpx", "application/gpx+xml"))
}
