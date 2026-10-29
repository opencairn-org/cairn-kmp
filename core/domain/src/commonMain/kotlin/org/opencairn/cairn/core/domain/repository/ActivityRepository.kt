package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview

/**
 * Offline-first access to activities: `observe*` emit from the local cache,
 * `refresh*` / `loadMore*` talk to the server and update the cache.
 */
interface ActivityRepository {
    fun observeSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Flow<ActivitySearchPage?>

    suspend fun refreshSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Unit>

    /** Appends the next page; false when there was nothing more. */
    suspend fun loadMoreSearch(
        filter: ActivityFilter,
        sort: ActivitySort,
    ): Result<Boolean>

    fun observeActivity(id: String): Flow<Cached<Activity>>

    suspend fun refreshActivity(id: String): Result<Unit>

    fun observeBestEfforts(activityId: String): Flow<Cached<List<BestEffort>>>

    suspend fun refreshBestEfforts(activityId: String): Result<Unit>

    fun observeAttachments(activityId: String): Flow<Cached<List<Attachment>>>

    suspend fun refreshAttachments(activityId: String): Result<Unit>
}

interface OverviewRepository {
    fun observeOverview(): Flow<Cached<Overview>>

    suspend fun refreshOverview(): Result<Unit>

    fun observeHomeFeed(): Flow<Cached<List<FeedItem>>>

    suspend fun refreshHomeFeed(): Result<Unit>
}
