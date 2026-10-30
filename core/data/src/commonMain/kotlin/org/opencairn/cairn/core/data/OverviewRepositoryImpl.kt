package org.opencairn.cairn.core.data

import cairn.v1.GetOverviewRequest
import cairn.v1.GetOverviewResponse
import cairn.v1.ListHomeFeedRequest
import cairn.v1.ListHomeFeedResponse
import cairn.v1.PageRequest
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.network.connect.apiResult

class OverviewRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : OverviewRepository {
    override fun observeOverview(): Flow<Cached<Overview>> =
        snapshots.observe(OVERVIEW) { GetOverviewResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshOverview(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .stats
                    .GetOverview()
                    .execute(GetOverviewRequest())
            snapshots.put(OVERVIEW, response.encode())
        }

    override fun observeHomeFeed(): Flow<Cached<List<FeedItem>>> =
        snapshots.observe(HOME_FEED) {
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
            snapshots.put(HOME_FEED, response.encode())
        }

    private companion object {
        const val OVERVIEW = "overview"
        const val HOME_FEED = "home-feed"
        const val FEED_SIZE = 30
    }
}
