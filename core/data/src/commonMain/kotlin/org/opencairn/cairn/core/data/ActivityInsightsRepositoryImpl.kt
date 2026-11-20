package org.opencairn.cairn.core.data

import cairn.v1.ExportActivityRequest
import cairn.v1.GetActivityZonesRequest
import cairn.v1.GetActivityZonesResponse
import cairn.v1.ListActivitySegmentEffortsRequest
import cairn.v1.ListActivitySegmentEffortsResponse
import cairn.v1.ListCommentsRequest
import cairn.v1.ListCommentsResponse
import cairn.v1.ListKudosRequest
import cairn.v1.ListKudosResponse
import cairn.v1.ListLapsRequest
import cairn.v1.ListLapsResponse
import cairn.v1.ListSimilarActivitiesRequest
import cairn.v1.ListSimilarActivitiesResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ExportFile
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.repository.ActivityInsightsRepository
import org.opencairn.cairn.core.network.CairnApi
import org.opencairn.cairn.core.network.connect.apiResult

class ActivityInsightsRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : ActivityInsightsRepository {
    override fun observeInsights(activityId: String): Flow<ActivityInsights> {
        val performance =
            combine(
                snapshots.observe(
                    "laps:$activityId",
                ) { b ->
                    ListLapsResponse.ADAPTER
                        .decode(b)
                        .laps
                        .map { it.toDomain() }
                },
                snapshots.observe("zones:$activityId") { GetActivityZonesResponse.ADAPTER.decode(it).toDomain() },
                snapshots.observe("segment-efforts:$activityId") { b ->
                    ListActivitySegmentEffortsResponse.ADAPTER
                        .decode(b)
                        .efforts
                        .map { it.toDomain() }
                },
            ) { laps, zones, efforts ->
                ActivityInsights(
                    laps = laps.value.orEmpty(),
                    zones = zones.value?.takeUnless { it.isEmpty },
                    segmentEfforts = efforts.value.orEmpty(),
                )
            }
        return combine(
            performance,
            snapshots.observe("similar:$activityId") { ListSimilarActivitiesResponse.ADAPTER.decode(it).toDomain() },
            snapshots.observe("kudos:$activityId") { ListKudosResponse.ADAPTER.decode(it).toDomain() },
            snapshots.observe("comments:$activityId") { b ->
                ListCommentsResponse.ADAPTER
                    .decode(b)
                    .comments
                    .map { it.toDomain() }
            },
        ) { insights, similar, kudos, comments ->
            insights.copy(
                similar = similar.value?.takeIf { it.hasOthers },
                kudos = kudos.value,
                comments = comments.value.orEmpty(),
            )
        }
    }

    override suspend fun refreshInsights(activityId: String): Result<Unit> {
        val api = apiResult { scope.api() }.getOrElse { return Result.failure(it) }
        val results =
            coroutineScope {
                parts(api, activityId)
                    .map { (key, fetch) ->
                        async { apiResult { snapshots.put(key, fetch()) } }
                    }.awaitAll()
            }
        // A part an older server lacks, or that the owner does not share, is not a failure of the page.
        val failure = results.mapNotNull { it.exceptionOrNull() }.firstOrNull { !it.isExpectedGap() }
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private fun parts(
        api: CairnApi,
        id: String,
    ): List<Pair<String, suspend () -> ByteArray>> =
        listOf(
            "laps:$id" to {
                api.activities
                    .ListLaps()
                    .execute(ListLapsRequest(activity_id = id))
                    .encode()
            },
            "zones:$id" to
                {
                    api.activities
                        .GetActivityZones()
                        .execute(GetActivityZonesRequest(activity_id = id))
                        .encode()
                },
            "segment-efforts:$id" to {
                api.activities
                    .ListActivitySegmentEfforts()
                    .execute(ListActivitySegmentEffortsRequest(activity_id = id))
                    .encode()
            },
            "similar:$id" to {
                api.activities
                    .ListSimilarActivities()
                    .execute(ListSimilarActivitiesRequest(activity_id = id))
                    .encode()
            },
            "kudos:$id" to {
                api.activities
                    .ListKudos()
                    .execute(ListKudosRequest(activity_id = id))
                    .encode()
            },
            "comments:$id" to {
                api.activities
                    .ListComments()
                    .execute(ListCommentsRequest(activity_id = id))
                    .encode()
            },
        )

    override suspend fun export(
        activityId: String,
        format: ExportFormat,
    ): Result<ExportFile> =
        apiResult {
            val request = ExportActivityRequest(activity_id = activityId, format = format.toProto())
            val response =
                scope
                    .api()
                    .activities
                    .ExportActivity()
                    .execute(request)
            val url = response.file_.urlOrNull() ?: throw AppError.Server("the server returned no download link")
            ExportFile(url, response.filename, response.content_type)
        }

    private fun Throwable.isExpectedGap() =
        this is AppError.Unimplemented || this is AppError.PermissionDenied || this is AppError.NotFound
}
