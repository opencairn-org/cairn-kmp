package org.opencairn.cairn.core.data

import app.cash.turbine.test
import cairn.v1.ActivityEvent
import cairn.v1.ActivityEventType
import cairn.v1.CountUnreadNotificationsResponse
import cairn.v1.ExternalAccount
import cairn.v1.ExternalAccountStatus
import cairn.v1.ListExternalAccountsResponse
import cairn.v1.ListNotificationsResponse
import cairn.v1.ListQueueItemsResponse
import cairn.v1.ListSyncHistoryResponse
import cairn.v1.MarkNotificationReadRequest
import cairn.v1.MarkNotificationReadResponse
import cairn.v1.NotificationEvent
import cairn.v1.NotificationEventType
import cairn.v1.NotificationSeverity
import cairn.v1.QueueCounts
import cairn.v1.QueueItem
import cairn.v1.RateLimitBudget
import cairn.v1.RateLimitWindow
import cairn.v1.SyncEvent
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.opencairn.cairn.core.domain.model.NotificationSeverity as DomainSeverity

class SyncAndNotificationRepositoryTest {
    @Test
    fun accountsCarryTheirSyncState() =
        runTest {
            val core = FakeCore().signIn()
            core.on("ExternalAccountService/ListExternalAccounts") {
                val strava =
                    ExternalAccount(
                        id = "x1",
                        provider = "strava",
                        status = ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_ACTIVE,
                        imported = 120,
                        provider_total = 150,
                        auto_import_enabled = true,
                        queue = QueueCounts(pending = 28, in_progress = 2, failed = 1),
                        rate_limit_budget = RateLimitBudget(short = RateLimitWindow(used = 90, limit = 100)),
                    )
                val garmin =
                    ExternalAccount(
                        id = "x2",
                        provider = "garmin",
                        display_label = "Watch",
                        status = ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_AUTH_INVALID,
                    )
                Reply.Ok(ListExternalAccountsResponse(accounts = listOf(strava, garmin)))
            }
            val repo = SyncRepositoryImpl(core.scope, core.snapshots)
            repo.refreshAccounts().getOrThrow()
            repo.observeAccounts().test {
                val (strava, garmin) = awaitItem().value!!
                assertEquals("Strava", strava.displayName)
                assertTrue(strava.healthy)
                assertEquals(30, strava.queue.open)
                assertEquals(90, strava.shortWindow?.used)
                // A window without a limit carries no information.
                assertNull(strava.dailyWindow)
                assertEquals("Watch", garmin.displayName)
                assertEquals(SyncAccountStatus.AUTH_INVALID, garmin.status)
                assertFalse(garmin.healthy)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun historyAndQueueAreKeptPerAccount() =
        runTest {
            val core = FakeCore().signIn()
            core.on("ExternalAccountService/ListSyncHistory") {
                Reply.Ok(
                    ListSyncHistoryResponse(events = listOf(SyncEvent(kind = "activity_imported", activity_id = "a1"))),
                )
            }
            core.on("ExternalAccountService/ListQueueItems") {
                Reply.Ok(
                    ListQueueItemsResponse(items = listOf(QueueItem(id = "q1", status = "failed", last_error = "429"))),
                )
            }
            val repo = SyncRepositoryImpl(core.scope, core.snapshots)
            repo.refreshHistory("x1").getOrThrow()
            repo.refreshQueue("x1").getOrThrow()
            repo.observeHistory("x1").test {
                assertEquals("a1", awaitItem().value?.single()?.activityId)
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeQueue("x1").test {
                assertEquals("429", awaitItem().value?.single()?.lastError)
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeQueue("x2").test {
                assertTrue(awaitItem().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun markingReadReloadsListAndBadge() =
        runTest {
            val core = FakeCore().signIn()
            var read = false
            var marked = ""
            core.on("NotificationService/ListNotifications") {
                val record =
                    NotificationEvent(
                        id = "n1",
                        type = NotificationEventType.NOTIFICATION_EVENT_TYPE_ACCOUNT_EXTERNAL_AUTH_EXPIRED,
                        severity = NotificationSeverity.NOTIFICATION_SEVERITY_WARN,
                        activity_id = "",
                        read = read,
                    )
                Reply.Ok(ListNotificationsResponse(notifications = listOf(record)))
            }
            core.on("NotificationService/CountUnreadNotifications") {
                Reply.Ok(CountUnreadNotificationsResponse(count = if (read) 0 else 1, count_warn = if (read) 0 else 1))
            }
            core.on("NotificationService/MarkNotificationRead") { body ->
                marked = MarkNotificationReadRequest.ADAPTER.decode(body).id
                read = true
                Reply.Ok(MarkNotificationReadResponse())
            }
            val repo = NotificationRepositoryImpl(core.scope, core.snapshots)
            repo.refreshNotifications().getOrThrow()
            repo.refreshUnread().getOrThrow()
            repo.observeNotifications().test {
                val first = awaitItem().value!!.single()
                assertEquals("External auth expired", first.title)
                assertEquals(DomainSeverity.WARN, first.severity)
                assertNull(first.activityId)
                assertEquals(1, first.count)
                assertFalse(first.read)
                repo.markRead("n1").getOrThrow()
                assertTrue(awaitItem().value!!.single().read)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("n1", marked)
            repo.observeUnread().test {
                assertEquals(0, awaitItem().value?.total)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun event(
        type: ActivityEventType,
        id: String = "",
    ) = FakeCore.frame(ActivityEvent(type = type, activity_id = id).encode())

    @Test
    fun liveChangesSkipHeartbeatsAndSurviveABrokenStream() =
        runTest {
            val core = FakeCore().signIn()
            core.on("ActivityService/StreamActivityEvents") {
                when (core.count("StreamActivityEvents")) {
                    // The first connection delivers a heartbeat and one change, then breaks off.
                    1 -> {
                        Reply.Stream(
                            event(ActivityEventType.ACTIVITY_EVENT_TYPE_UNSPECIFIED) +
                                event(ActivityEventType.ACTIVITY_EVENT_TYPE_ACTIVITY_CREATED, "a1"),
                        )
                    }

                    2 -> {
                        Reply.Stream(event(ActivityEventType.ACTIVITY_EVENT_TYPE_NEW_PR, "a2"))
                    }

                    else -> {
                        Reply.Failure("unimplemented", HttpStatusCode.NotImplemented)
                    }
                }
            }
            val sharing = CoroutineScope(Dispatchers.Default)
            val live = LiveEventsRepositoryImpl(core.scope, sharing, firstPause = 5.milliseconds)
            live.activityChanges.test {
                assertEquals(ActivityChange(ActivityChange.Kind.CREATED, "a1"), awaitItem())
                assertEquals(ActivityChange(ActivityChange.Kind.RECORD, "a2"), awaitItem())
                // The third attempt is refused for good: no further connection is opened.
                withContext(Dispatchers.Default) {
                    withTimeout(3.seconds) { while (core.count("StreamActivityEvents") < 3) delay(5.milliseconds) }
                    delay(100.milliseconds)
                }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(3, core.count("StreamActivityEvents"))
            sharing.cancel()
        }
}
