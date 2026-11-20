package org.opencairn.cairn.desktop

import app.cash.turbine.test
import cairn.v1.StreamActivityEventsRequest
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.app.di.PlatformContext
import org.opencairn.cairn.core.auth.AuthFlow
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.DesktopSecretStore
import org.opencairn.cairn.core.auth.SecretStore
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the real app graph against a running cairn-core when CAIRN_E2E_BASE_URL and
 * CAIRN_E2E_TOKEN (a personal access token) are set; skipped otherwise. `make e2e` wires it up.
 */
class EndToEndTest {
    private val baseUrl = System.getenv("CAIRN_E2E_BASE_URL")
    private val token = System.getenv("CAIRN_E2E_TOKEN")

    private fun graph(): AppGraph {
        val dir = Files.createTempDirectory("cairn-e2e").toFile()
        val platform =
            object : PlatformContext {
                override val appVersion = "e2e"
                override val databaseDriverFactory = DatabaseDriverFactory(dir)
                override val authPlatform =
                    object : AuthPlatform {
                        override val redirectUri = "http://127.0.0.1:8765/callback"
                        override val authFlow = AuthFlow { error("browser flow is not part of the e2e test") }
                        override val secretStore: SecretStore = DesktopSecretStore(File(dir, "secrets"))
                    }
            }
        return createGraphFactory<AppGraph.Factory>().create(platform)
    }

    @Test
    fun signInWithTokenThenReadEverythingTheScreensNeed() =
        runTest(timeout = 120.seconds) {
            if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) {
                println("skipped: CAIRN_E2E_BASE_URL / CAIRN_E2E_TOKEN not set")
                return@runTest
            }
            val g = graph()
            signIn(g, baseUrl, token)
            checkOverview(g)
            val rides = checkRideSearch(g)
            checkDetail(g, rides.first().id)
            checkInsights(g, rides.first().id)
            checkAnalytics(g)
            checkSyncAndNotifications(g)
            checkLiveStream(g)
            // Same graph, new subscription = what a restart sees: the cache answers before any network call.
            g.activityRepository.observeSearch(rideFilter, ActivitySort.DISTANCE_DESC).test {
                assertEquals(rides.map { it.id }, assertNotNull(awaitItem()).activities.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            g.sessionRepository.signOut()
            g.sessionRepository.currentUser.test {
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private val rideFilter = ActivityFilter(types = setOf(ActivityType.RIDE))

    private suspend fun signIn(
        g: AppGraph,
        baseUrl: String,
        token: String,
    ) {
        val server = g.sessionRepository.discoverServer(baseUrl).getOrThrow()
        println("server ${server.host} version=${server.version} oauth=${server.oauthEnabled}")
        assertTrue(server.oauthEnabled)
        val user = g.sessionRepository.signInWithToken(server, token).getOrThrow()
        println("signed in as ${user.username} (${user.email})")
        assertEquals("admin", user.username)
    }

    private suspend fun checkOverview(g: AppGraph) {
        g.overviewRepository.refreshOverview().getOrThrow()
        g.overviewRepository.refreshHomeFeed().getOrThrow()
        g.overviewRepository.observeOverview().test {
            val overview = assertNotNull(awaitItem().value)
            println("overview: ${overview.totals.count} activities, ${overview.totals.distance.kilometers} km")
            assertTrue(overview.totals.count >= 7)
            assertTrue(overview.recent.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        g.overviewRepository.observeHomeFeed().test {
            val feed = assertNotNull(awaitItem().value)
            println("home feed: ${feed.size} items, first=${feed.firstOrNull()?.title}")
            assertTrue(feed.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun checkRideSearch(g: AppGraph): List<Activity> {
        g.activityRepository.refreshSearch(rideFilter, ActivitySort.DISTANCE_DESC).getOrThrow()
        var rides = emptyList<Activity>()
        g.activityRepository.observeSearch(rideFilter, ActivitySort.DISTANCE_DESC).test {
            val page = assertNotNull(awaitItem())
            println(
                "rides: ${page.activities.map {
                    it.title + " " + it.summary.distance?.kilometers
                }} facets=${page.facets.types}",
            )
            assertTrue(page.activities.all { it.type == ActivityType.RIDE })
            val distances = page.activities.map { it.summary.distance?.value ?: 0.0 }
            assertEquals(distances.sortedDescending(), distances)
            cancelAndIgnoreRemainingEvents()
            rides = page.activities
        }
        return rides
    }

    private suspend fun checkDetail(
        g: AppGraph,
        id: String,
    ) {
        g.activityRepository.refreshActivity(id).getOrThrow()
        g.activityRepository.refreshBestEfforts(id)
        g.activityRepository.refreshAttachments(id)
        g.activityRepository.observeActivity(id).test {
            val a = assertNotNull(awaitItem().value)
            println("detail: ${a.displayTitle} ${a.summary} sources=${a.sources.map { it.provider }}")
            assertEquals(id, a.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Every part is optional on the server's side; what matters is that the calls decode and none fails hard. */
    private suspend fun checkInsights(
        g: AppGraph,
        id: String,
    ) {
        g.insightsRepository.refreshInsights(id).getOrThrow()
        g.insightsRepository.observeInsights(id).test {
            val i = awaitItem()
            println(
                "insights: laps=${i.laps.size} zones=${i.zones != null} segments=${i.segmentEfforts.size} " +
                    "similar=${i.similar?.activities?.size ?: 0} kudos=${i.kudos?.count} comments=${i.comments.size}",
            )
            cancelAndIgnoreRemainingEvents()
        }
        // The server exports recorded data only; the app offers export on the same condition.
        val recorded =
            g.activityRepository
                .observeActivity(id)
                .first()
                .value
                ?.hasStream == true
        if (recorded) {
            val file = g.insightsRepository.export(id, ExportFormat.GPX).getOrThrow()
            println("export: ${file.filename} (${file.contentType})")
            assertTrue(file.url.startsWith("http"))
        } else {
            val refusal = g.insightsRepository.export(id, ExportFormat.GPX).exceptionOrNull()
            println("export: not offered, the activity has no stream (server says: ${refusal?.message})")
            assertNotNull(refusal)
        }
    }

    private suspend fun checkAnalytics(g: AppGraph) {
        g.analyticsRepository.refreshTrainingLoad(TrainingLoadRange.SIX_MONTHS).getOrThrow()
        g.analyticsRepository.observeTrainingLoad(TrainingLoadRange.SIX_MONTHS).test {
            val load = assertNotNull(awaitItem().value)
            println("training load: ${load.days.size} days, fitness=${load.fitness} form=${load.form}")
            cancelAndIgnoreRemainingEvents()
        }
        g.analyticsRepository.refreshStats().getOrThrow()
        g.analyticsRepository.observeStats().test {
            val stats = assertNotNull(awaitItem().value)
            println("stats: ${stats.totals.count} activities over ${stats.byYear.size} years, sports=${stats.bySport}")
            assertTrue(stats.totals.count >= 7)
            cancelAndIgnoreRemainingEvents()
        }
        val heatmap = g.analyticsRepository.heatmap(ActivityFilter()).getOrThrow()
        println(
            "heatmap: ${heatmap.withTrack} of ${heatmap.matched} with a track, ${heatmap.tracks.sumOf {
                it.points.size
            }} points",
        )
        assertEquals(heatmap.withTrack, heatmap.tracks.size)
    }

    private suspend fun checkSyncAndNotifications(g: AppGraph) {
        g.syncRepository.refreshAccounts().getOrThrow()
        g.syncRepository.observeAccounts().test {
            val accounts = assertNotNull(awaitItem().value)
            println("connections: ${accounts.map { it.displayName + " " + it.status }}")
            cancelAndIgnoreRemainingEvents()
        }
        g.notificationRepository.refreshNotifications().getOrThrow()
        g.notificationRepository.refreshUnread().getOrThrow()
        g.notificationRepository.observeUnread().test {
            println("notifications: ${assertNotNull(awaitItem().value).total} unread")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The activity stream must open and stay open. The app's own live updates retry quietly when
     * a stream fails, so this reads the stream itself: an end or an error within the hold time
     * fails the run. No event is expected unless something changes on the server meanwhile;
     * CAIRN_E2E_LIVE_SECONDS holds longer (35 or more also sees the server's 30 s heartbeat).
     */
    private suspend fun checkLiveStream(g: AppGraph) {
        val hold = (System.getenv("CAIRN_E2E_LIVE_SECONDS")?.toIntOrNull() ?: 3).seconds
        withContext(Dispatchers.Default) {
            coroutineScope {
                val call =
                    g.session
                        .api()
                        .activities
                        .StreamActivityEvents()
                val events = call.executeIn(this, StreamActivityEventsRequest())
                val first = withTimeoutOrNull(hold) { events.receiveCatching() }
                call.cancel()
                first?.exceptionOrNull()?.let { throw AssertionError("the activity stream failed: ${it.message}", it) }
                check(first == null || !first.isClosed) { "the activity stream ended by itself" }
                val event = first?.getOrNull()
                println("live: stream held for up to $hold, event=${event?.type} ${event?.activity_id.orEmpty()}")
            }
        }
    }
}
