package org.opencairn.cairn.feature.activities

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.ActivitySummary
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.ExportFile
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.GearSetBy
import org.opencairn.cairn.core.domain.model.Privacy
import org.opencairn.cairn.core.domain.repository.ActivityInsightsRepository
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun activity(
    id: String,
    title: String = "Ride $id",
) = Activity(
    id = id,
    type = ActivityType.RIDE,
    discipline = "",
    customSubtype = "",
    isVirtual = false,
    isEbike = false,
    isCommute = false,
    isRace = false,
    title = title,
    description = "",
    startTime = Instant.fromEpochSeconds(1_790_000_000),
    endTime = null,
    elapsed = 62.minutes,
    moving = 60.minutes,
    timezone = "UTC",
    summary = ActivitySummary(),
    tags = emptyList(),
    gearId = null,
    gearName = null,
    gearSetBy = GearSetBy.UNSPECIFIED,
    gearUnset = false,
    privacy = Privacy.PRIVATE,
    startPlace = "",
    startLat = null,
    startLng = null,
    mapImageUrl = null,
    mapImageEtag = "",
    primaryStreamSourceId = "",
    sources = emptyList(),
    updatedAt = null,
)

class FakeActivities : ActivityRepository {
    val activities = MutableStateFlow<Map<String, Activity>>(emptyMap())
    val page = MutableStateFlow<ActivitySearchPage?>(null)
    val refreshedActivities = mutableListOf<String>()
    val searches = mutableListOf<Pair<ActivityFilter, ActivitySort>>()
    var fail: Throwable? = null
    var hasMore = false

    private fun outcome(): Result<Unit> = fail?.let { Result.failure(it) } ?: Result.success(Unit)

    override fun observeSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Flow<ActivitySearchPage?> = page

    override suspend fun refreshSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Unit> {
        searches += filter to sort
        return outcome()
    }

    override suspend fun loadMoreSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Boolean> = fail?.let { Result.failure(it) } ?: Result.success(hasMore)

    override fun observeActivity(id: String): Flow<Cached<Activity>> =
        flowOf(Cached(activities.value[id], Instant.fromEpochSeconds(1)))

    override suspend fun refreshActivity(id: String): Result<Unit> {
        refreshedActivities += id
        return outcome()
    }

    override fun observeBestEfforts(activityId: String): Flow<Cached<List<BestEffort>>> =
        flowOf(Cached(emptyList(), null))

    override suspend fun refreshBestEfforts(activityId: String): Result<Unit> =
        Result.failure(AppError.Unimplemented("/cairn.v1.ActivityService/ListBestEfforts"))

    override fun observeAttachments(activityId: String): Flow<Cached<List<Attachment>>> =
        flowOf(Cached(emptyList(), null))

    override suspend fun refreshAttachments(activityId: String): Result<Unit> = Result.success(Unit)

    override fun observeStream(sourceId: String): Flow<Cached<ActivityStream>> = flowOf(Cached(null, null))

    override suspend fun refreshStream(sourceId: String): Result<Unit> = Result.success(Unit)
}

class FakeInsights : ActivityInsightsRepository {
    val insights = MutableStateFlow(ActivityInsights())
    val exports = mutableListOf<ExportFormat>()
    var refreshFailure: Throwable? = null
    var exportFailure: Throwable? = null

    override fun observeInsights(activityId: String): Flow<ActivityInsights> = insights

    override suspend fun refreshInsights(activityId: String): Result<Unit> =
        refreshFailure?.let { Result.failure(it) } ?: Result.success(Unit)

    override suspend fun export(
        activityId: String,
        format: ExportFormat,
    ): Result<ExportFile> {
        exports += format
        return exportFailure?.let { Result.failure(it) }
            ?: Result.success(
                ExportFile("https://c/dl/$activityId", "ride.${format.name.lowercase()}", "application/xml"),
            )
    }
}

suspend fun <T> ReceiveTurbine<T>.awaitItemWith(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
