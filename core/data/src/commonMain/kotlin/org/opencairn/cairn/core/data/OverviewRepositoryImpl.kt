package org.opencairn.cairn.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import cairn.v1.GetOverviewRequest
import cairn.v1.GetOverviewResponse
import cairn.v1.ListHomeFeedRequest
import cairn.v1.ListHomeFeedResponse
import cairn.v1.PageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.network.connect.apiResult
import kotlin.time.Clock

class OverviewRepositoryImpl(
    private val db: CairnDb,
    private val scope: CacheScope,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.System,
) : OverviewRepository {
    override fun observeOverview(): Flow<Cached<Overview>> =
        snapshot(OVERVIEW) { GetOverviewResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshOverview(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .stats
                    .GetOverview()
                    .execute(GetOverviewRequest())
            putSnapshot(OVERVIEW, response.encode())
        }

    override fun observeHomeFeed(): Flow<Cached<List<FeedItem>>> =
        snapshot(HOME_FEED) {
            ListHomeFeedResponse.ADAPTER
                .decode(it)
                .items
                .mapNotNull { i -> i.toDomain() }
        }

    override suspend fun refreshHomeFeed(): Result<Unit> =
        apiResult {
            val request = ListHomeFeedRequest(page = PageRequest(limit = FEED_SIZE))
            val response =
                scope
                    .api()
                    .social
                    .ListHomeFeed()
                    .execute(request)
            putSnapshot(HOME_FEED, response.encode())
        }

    private fun <T> snapshot(
        key: String,
        decode: (ByteArray) -> T,
    ): Flow<Cached<T>> =
        scope.perAccount { account ->
            if (account == null) return@perAccount flowOf(Cached(null, null))
            db.cacheQueries
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
        withContext(dispatchers.io) { db.cacheQueries.upsertSnapshot(account, key, bytes, clock.now().epochSeconds) }
    }

    private companion object {
        const val OVERVIEW = "overview"
        const val HOME_FEED = "home-feed"
        const val FEED_SIZE = 30
    }
}
