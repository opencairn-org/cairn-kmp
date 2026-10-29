package org.opencairn.cairn.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import cairn.v1.ActivityFacets
import cairn.v1.GetActivityRequest
import cairn.v1.ListAttachmentsRequest
import cairn.v1.ListAttachmentsResponse
import cairn.v1.ListBestEffortsRequest
import cairn.v1.ListBestEffortsResponse
import cairn.v1.PageRequest
import cairn.v1.SearchActivitiesRequest
import cairn.v1.SearchActivitiesResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.database.Snapshot
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Clock
import kotlin.time.Instant

class ActivityRepositoryImpl(
    private val db: CairnDb,
    private val scope: CacheScope,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.System,
) : ActivityRepository {
    private val q get() = db.cacheQueries

    override fun observeSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Flow<ActivitySearchPage?> =
        scope.perAccount { account ->
            if (account == null) return@perAccount flowOf(null)
            val hash = queryHash(filter, sort)
            val rows = q.searchPage(account, hash).asFlow().mapToList(dispatchers.io)
            val meta = q.searchMeta(account, hash).asFlow().mapToOneOrNull(dispatchers.io)
            combine(rows, meta) { activities, m ->
                if (m == null) return@combine null
                ActivitySearchPage(
                    activities =
                        activities.map {
                            cairn.v1.Activity.ADAPTER
                                .decode(it.proto)
                                .toDomain()
                        },
                    facets = ActivityFacets.ADAPTER.decode(m.facets).toDomain(),
                    total = m.total.toInt(),
                    matched = m.matched.toInt(),
                    hasMore = m.next_cursor.isNotEmpty(),
                    fetchedAt = Instant.fromEpochSeconds(m.fetched_at),
                )
            }
        }

    override suspend fun refreshSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Unit> =
        apiResult {
            val account = scope.account()
            val response = search(filter, sort, cursor = "")
            storeSearch(account, queryHash(filter, sort), response, append = false)
        }

    override suspend fun loadMoreSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Boolean> =
        apiResult {
            val account = scope.account()
            val hash = queryHash(filter, sort)
            val cursor = withContext(dispatchers.io) { q.searchMeta(account, hash).executeAsOneOrNull()?.next_cursor }
            if (cursor.isNullOrEmpty()) return@apiResult false
            val response = search(filter, sort, cursor)
            storeSearch(account, hash, response, append = true)
            response.page?.next_cursor?.isNotEmpty() == true
        }

    private suspend fun search(
        filter: ActivityFilter,
        sort: ActivitySort,
        cursor: String,
    ): SearchActivitiesResponse =
        scope.api().activities.SearchActivities().execute(
            SearchActivitiesRequest(
                filter = filter.toProto(),
                sort = sort.toProto(),
                page = PageRequest(cursor = cursor, limit = PAGE_SIZE),
            ),
        )

    private suspend fun storeSearch(
        account: String,
        hash: String,
        response: SearchActivitiesResponse,
        append: Boolean,
    ) = withContext(dispatchers.io) {
        val now = clock.now().epochSeconds
        db.transaction {
            val start = if (append) q.searchCount(account, hash).executeAsOne().toInt() else 0
            if (!append) q.clearSearch(account, hash)
            response.activities.forEachIndexed { i, a ->
                upsertActivity(account, a, now)
                q.insertSearchResult(account, hash, (start + i).toLong(), a.id)
            }
            q.upsertSearchMeta(
                account,
                hash,
                (response.facets ?: ActivityFacets()).encode(),
                response.total.toLong(),
                response.matched.toLong(),
                response.page?.next_cursor ?: "",
                now,
            )
        }
    }

    private fun upsertActivity(
        account: String,
        a: cairn.v1.Activity,
        now: Long,
    ) {
        q.upsertActivity(
            account,
            a.id,
            a.start_time?.getEpochSecond() ?: 0L,
            a.type.name,
            a.discipline.name,
            a.title,
            a.encode(),
            a.updated_at?.getEpochSecond(),
            now,
        )
    }

    override fun observeActivity(id: String): Flow<Cached<Activity>> =
        scope.perAccount { account ->
            if (account == null) return@perAccount flowOf(Cached(null, null))
            q.activityById(account, id).asFlow().mapToOneOrNull(dispatchers.io).map { row ->
                Cached(
                    row?.let {
                        cairn.v1.Activity.ADAPTER
                            .decode(it.proto)
                            .toDomain()
                    },
                    row?.let { Instant.fromEpochSeconds(it.fetched_at) },
                )
            }
        }

    override suspend fun refreshActivity(id: String): Result<Unit> =
        apiResult {
            val account = scope.account()
            val response =
                scope
                    .api()
                    .activities
                    .GetActivity()
                    .execute(GetActivityRequest(id = id))
            val activity = requireNotNull(response.activity) { "no activity in response" }
            withContext(dispatchers.io) { upsertActivity(account, activity, clock.now().epochSeconds) }
        }

    override fun observeBestEfforts(activityId: String): Flow<Cached<List<BestEffort>>> =
        snapshot("best-efforts:$activityId") { bytes ->
            ListBestEffortsResponse.ADAPTER
                .decode(bytes)
                .efforts
                .map { it.toDomain() }
        }

    override suspend fun refreshBestEfforts(activityId: String): Result<Unit> =
        apiResult {
            val request = ListBestEffortsRequest(activity_id = activityId)
            val response =
                scope
                    .api()
                    .activities
                    .ListBestEfforts()
                    .execute(request)
            putSnapshot("best-efforts:$activityId", response.encode())
        }

    override fun observeAttachments(activityId: String): Flow<Cached<List<Attachment>>> =
        snapshot("attachments:$activityId") { bytes ->
            ListAttachmentsResponse.ADAPTER
                .decode(bytes)
                .attachments
                .map { it.toDomain() }
        }

    override suspend fun refreshAttachments(activityId: String): Result<Unit> =
        apiResult {
            val request = ListAttachmentsRequest(activity_id = activityId)
            val response =
                scope
                    .api()
                    .activities
                    .ListAttachments()
                    .execute(request)
            putSnapshot("attachments:$activityId", response.encode())
        }

    private fun <T> snapshot(
        key: String,
        decode: (ByteArray) -> T,
    ): Flow<Cached<T>> =
        scope.perAccount { account ->
            if (account == null) return@perAccount flowOf(Cached(null, null))
            q
                .snapshotByKey(account, key)
                .asFlow()
                .mapToOneOrNull(dispatchers.io)
                .map { it.toCached(decode) }
        }

    private suspend fun putSnapshot(
        key: String,
        bytes: ByteArray,
    ) {
        val account = scope.account()
        withContext(dispatchers.io) { q.upsertSnapshot(account, key, bytes, clock.now().epochSeconds) }
    }

    private companion object {
        const val PAGE_SIZE = 50

        fun queryHash(
            filter: ActivityFilter,
            sort: ActivitySort,
        ) = cacheKey("search", filter, sort)
    }
}

internal fun <T> Snapshot?.toCached(decode: (ByteArray) -> T): Cached<T> =
    Cached(this?.let { decode(it.bytes) }, this?.let { Instant.fromEpochSeconds(it.fetched_at) })
