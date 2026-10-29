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
            val server = g.sessionRepository.discoverServer(baseUrl).getOrThrow()
            println("server ${server.host} version=${server.version} oauth=${server.oauthEnabled}")
            assertTrue(server.oauthEnabled)
            val user = g.sessionRepository.signInWithToken(server, token).getOrThrow()
            println("signed in as ${user.username} (${user.email})")
            assertEquals("admin", user.username)

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

            val filter = ActivityFilter(types = setOf(ActivityType.RIDE))
            g.activityRepository.refreshSearch(filter, ActivitySort.DISTANCE_DESC).getOrThrow()
            var rides = emptyList<org.opencairn.cairn.core.domain.model.Activity>()
            g.activityRepository.observeSearch(filter, ActivitySort.DISTANCE_DESC).test {
                val page = assertNotNull(awaitItem())
                println(
                    "rides: ${page.activities.map {
                        it.title + " " + it.summary.distance?.kilometers
                    }} facets=${page.facets.types}",
                )
                assertTrue(page.activities.all { it.type == ActivityType.RIDE })
                assertEquals(
                    page.activities.map { it.summary.distance?.value ?: 0.0 }.sortedDescending(),
                    page.activities.map {
                        it.summary.distance?.value
                            ?: 0.0
                    },
                )
                cancelAndIgnoreRemainingEvents()
                rides = page.activities
            }

            val first = rides.first()
            g.activityRepository.refreshActivity(first.id).getOrThrow()
            g.activityRepository.refreshBestEfforts(first.id)
            g.activityRepository.refreshAttachments(first.id)
            g.activityRepository.observeActivity(first.id).test {
                val a = assertNotNull(awaitItem().value)
                println("detail: ${a.displayTitle} ${a.summary} sources=${a.sources.map { it.provider }}")
                assertEquals(first.id, a.id)
                cancelAndIgnoreRemainingEvents()
            }

            // Second graph instance = app restart: the cache answers before any network call.
            val restarted = g
            restarted.activityRepository.observeSearch(filter, ActivitySort.DISTANCE_DESC).test {
                assertEquals(rides.map { it.id }, assertNotNull(awaitItem()).activities.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            g.sessionRepository.signOut()
            g.sessionRepository.currentUser.test {
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
