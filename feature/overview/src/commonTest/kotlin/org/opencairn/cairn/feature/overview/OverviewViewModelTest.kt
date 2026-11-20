package org.opencairn.cairn.feature.overview

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.model.QueueCounts
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.model.SyncNotice
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.domain.repository.SyncRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant

private val at = Instant.fromEpochSeconds(1)
private val noTotals = Totals(0, Meters(0.0), Duration.ZERO, Meters(0.0))

private fun feedItem(
    id: String,
    source: FeedItem.Source,
) = FeedItem(source, null, id, "Ride $id", ActivityType.RIDE, "ride", at, null, null, null, "", null, null)

private class FakeOverview : OverviewRepository {
    var refreshes = 0
    var fail: Throwable? = null

    override fun observeOverview(): Flow<Cached<Overview>> =
        flowOf(Cached(Overview(noTotals, emptyList(), emptyList(), noTotals, noTotals, null, null), at))

    override suspend fun refreshOverview(): Result<Unit> {
        refreshes++
        return fail?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override fun observeHomeFeed(): Flow<Cached<List<FeedItem>>> =
        flowOf(Cached(listOf(feedItem("a1", FeedItem.Source.SELF), feedItem("b1", FeedItem.Source.FOLLOWING)), at))

    override suspend fun refreshHomeFeed(): Result<Unit> = Result.success(Unit)
}

private class FakeSync : SyncRepository {
    val accounts = MutableStateFlow<List<SyncAccount>?>(null)
    var fail: Throwable? = null

    override fun observeAccounts(): Flow<Cached<List<SyncAccount>>> = accounts.map { Cached(it, at) }

    override suspend fun refreshAccounts(): Result<Unit> = fail?.let { Result.failure(it) } ?: Result.success(Unit)

    override fun observeHistory(accountId: String): Flow<Cached<List<SyncEvent>>> = flowOf(Cached(null, null))

    override suspend fun refreshHistory(accountId: String): Result<Unit> = Result.success(Unit)

    override fun observeQueue(accountId: String): Flow<Cached<List<QueueItem>>> = flowOf(Cached(null, null))

    override suspend fun refreshQueue(accountId: String): Result<Unit> = Result.success(Unit)
}

private fun importing(pending: Int) =
    SyncAccount(
        id = "x1",
        provider = "strava",
        label = "",
        status = SyncAccountStatus.ACTIVE,
        statusReason = "",
        lastSyncAt = null,
        lastSuccessfulSyncAt = null,
        imported = 0,
        providerTotal = 0,
        newestActivityAt = null,
        discovering = false,
        rateLimited = false,
        autoImport = true,
        queue = QueueCounts(pending = pending),
        shortWindow = null,
        dailyWindow = null,
    )

private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

class OverviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val overview = FakeOverview()
    private val sync = FakeSync()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun feedFilterAndSyncNoticeFollowTheirSources() =
        runTest(dispatcher) {
            // Connections refused (an older sign-in): the page works, just without the sync line.
            sync.fail = AppError.PermissionDenied()
            val vm = OverviewViewModel(overview, sync)
            vm.state.test {
                val st = awaitMatching { it.overview.value != null && !it.refreshing }
                assertNull(st.error)
                assertNull(st.syncNotice)
                assertEquals(2, st.visibleFeed.size)
                vm.setFeedFilter(FeedItem.Source.FOLLOWING)
                assertEquals(listOf("b1"), awaitMatching { it.feedFilter != null }.visibleFeed.map { it.id })
                sync.accounts.value = listOf(importing(3))
                val notice = awaitMatching { it.syncNotice != null }.syncNotice
                assertEquals(SyncNotice(SyncNotice.Kind.IMPORTING, "Strava", 3), notice)
                sync.accounts.value = listOf(importing(0))
                assertNull(awaitMatching { it.syncNotice == null }.syncNotice)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aLiveChangeReloadsAndFailuresShow() =
        runTest(dispatcher) {
            val live = MutableSharedFlow<ActivityChange>(extraBufferCapacity = 8)
            val vm = OverviewViewModel(overview, sync, live)
            vm.state.test {
                awaitMatching { it.overview.value != null && !it.refreshing }
                assertEquals(1, overview.refreshes)
                overview.fail = AppError.Network()
                live.emit(ActivityChange(ActivityChange.Kind.CREATED, "a9"))
                assertEquals("Offline, showing cached data", awaitMatching { it.error != null }.error)
                assertEquals(2, overview.refreshes)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
