package org.opencairn.cairn.desktop

import app.cash.turbine.test
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.runTest
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
        runTest(timeout = 60.seconds) {
            if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) {
                println("skipped: CAIRN_E2E_BASE_URL / CAIRN_E2E_TOKEN not set")
                return@runTest
            }
            val g = graph()
            signIn(g, baseUrl, token)
            checkOverview(g)
            val rides = checkRideSearch(g)
            checkDetail(g, rides.first().id)
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
}
