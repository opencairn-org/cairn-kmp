package org.opencairn.cairn.feature.account

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.NotificationSeverity
import org.opencairn.cairn.core.domain.model.QueueCounts
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.model.UnreadCount
import org.opencairn.cairn.core.domain.repository.NotificationRepository
import org.opencairn.cairn.core.domain.repository.SyncRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val at = Instant.fromEpochSeconds(1)

private fun syncAccount(id: String) =
    SyncAccount(
        id = id,
        provider = "strava",
        label = "",
        status = SyncAccountStatus.ACTIVE,
        statusReason = "",
        lastSyncAt = null,
        lastSuccessfulSyncAt = null,
        imported = 10,
        providerTotal = 12,
        newestActivityAt = null,
        discovering = false,
        rateLimited = false,
        autoImport = true,
        queue = QueueCounts(pending = 2),
        shortWindow = null,
        dailyWindow = null,
    )

private class FakeSync : SyncRepository {
    val accounts = MutableStateFlow<List<SyncAccount>?>(listOf(syncAccount("x1")))
    var accountRefreshes = 0
    val detailRefreshes = mutableListOf<String>()
    var fail: Throwable? = null

    private fun outcome(): Result<Unit> = fail?.let { Result.failure(it) } ?: Result.success(Unit)

    override fun observeAccounts(): Flow<Cached<List<SyncAccount>>> = accounts.map { Cached(it, at) }

    override suspend fun refreshAccounts(): Result<Unit> {
        accountRefreshes++
        return outcome()
    }

    override fun observeHistory(accountId: String): Flow<Cached<List<SyncEvent>>> =
        MutableStateFlow(Cached(listOf(SyncEvent("activity_imported", 1, "", at, "a1", "completed")), at))

    override suspend fun refreshHistory(accountId: String): Result<Unit> {
        detailRefreshes += "history:$accountId"
        return outcome()
    }

    override fun observeQueue(accountId: String): Flow<Cached<List<QueueItem>>> =
        MutableStateFlow(Cached(listOf(QueueItem("q1", "activity", "991", "pending", 0, "", at, null)), at))

    override suspend fun refreshQueue(accountId: String): Result<Unit> {
        detailRefreshes += "queue:$accountId"
        return outcome()
    }
}

private fun notification(
    id: String,
    read: Boolean,
) = Notification(id, "sync_import_completed", NotificationSeverity.INFO, emptyMap(), null, 1, read, at)

private class FakeNotifications : NotificationRepository {
    val list = MutableStateFlow(listOf(notification("n1", read = false), notification("n2", read = true)))
    var refreshes = 0
    var fail: Throwable? = null
    var changeFailure: Throwable? = null

    private fun unreadCount() = UnreadCount(total = list.value.count { !it.read })

    override fun observeNotifications(): Flow<Cached<List<Notification>>> = list.map { Cached(it, at) }

    override suspend fun refreshNotifications(): Result<Unit> {
        refreshes++
        return fail?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override fun observeUnread(): Flow<Cached<UnreadCount>> = list.map { Cached(unreadCount(), at) }

    override suspend fun refreshUnread(): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(id: String): Result<Unit> {
        changeFailure?.let { return Result.failure(it) }
        list.value = list.value.map { if (it.id == id) it.copy(read = true) else it }
        return Result.success(Unit)
    }

    override suspend fun markAllRead(): Result<Unit> {
        list.value = list.value.map { it.copy(read = true) }
        return Result.success(Unit)
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

class SyncViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun connectionsPollOnlyWhileSomebodyWatches() =
        runTest(dispatcher) {
            val repo = FakeSync()
            val vm = ConnectionsViewModel(repo, pollEvery = 5.seconds)
            vm.state.test {
                val st = awaitMatching { it.accounts.value != null && !it.refreshing }
                assertEquals(2, st.account("x1")?.queue?.open)
                assertNull(st.account("nope"))
                advanceTimeBy(11.seconds)
                cancelAndIgnoreRemainingEvents()
            }
            // Now, after five and after ten seconds.
            assertEquals(3, repo.accountRefreshes)
            assertEquals(emptyList(), repo.detailRefreshes)
            // Nobody is looking any more: after the grace period the page stops asking.
            advanceTimeBy(60.seconds)
            assertTrue(repo.accountRefreshes <= 4, "polled ${repo.accountRefreshes} times")
        }

    @Test
    fun connectionDetailLoadsHistoryAndQueueAndReportsFailures() =
        runTest(dispatcher) {
            val repo = FakeSync().apply { fail = AppError.Network() }
            val vm = ConnectionsViewModel(repo, detailOf = "x1")
            vm.state.test {
                val st = awaitMatching { it.error != null && it.history.isNotEmpty() }
                assertEquals("Offline, showing cached data", st.error)
                assertEquals("a1", st.history.single().activityId)
                assertEquals("991", st.queue.single().externalId)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("history:x1", "queue:x1"), repo.detailRefreshes.take(2))
        }

    @Test
    fun notificationsFilterMarkReadAndFollowArrivals() =
        runTest(dispatcher) {
            val repo = FakeNotifications()
            val arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
            val vm = NotificationsViewModel(repo, arrivals)
            vm.state.test {
                val first = awaitMatching { it.notifications.value != null && !it.refreshing }
                assertEquals(1, first.unread.total)
                assertEquals(listOf("n1", "n2"), first.visible.map { it.id })
                vm.setUnreadOnly(true)
                assertEquals(listOf("n1"), awaitMatching { it.unreadOnly }.visible.map { it.id })
                vm.markRead("n1")
                val read = awaitMatching { it.unread.total == 0 }
                assertEquals(emptyList(), read.visible)
                val before = repo.refreshes
                arrivals.emit(Unit)
                testScheduler.runCurrent()
                assertEquals(before + 1, repo.refreshes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun notificationsOnAServerWithoutThemAreSimplyEmptyAndFailedChangesShow() =
        runTest(dispatcher) {
            val repo =
                FakeNotifications().apply {
                    fail =
                        AppError.Unimplemented("/cairn.v1.NotificationService/ListNotifications")
                }
            val vm = NotificationsViewModel(repo)
            vm.state.test {
                assertNull(awaitMatching { it.notifications.value != null && !it.refreshing }.error)
                repo.changeFailure = AppError.PermissionDenied("this OAuth token lacks the notifications:write scope")
                vm.markRead("n1")
                val denied = awaitMatching { it.error != null }
                assertTrue(denied.error!!.startsWith("This sign-in may not do that yet"), denied.error)
                vm.markAllRead()
                assertEquals(0, awaitMatching { it.unread.total == 0 }.unread.total)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
