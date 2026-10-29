package org.opencairn.cairn.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import cairn.v1.ActivityFacets
import cairn.v1.Facet
import cairn.v1.GetActivityResponse
import cairn.v1.GetCurrentUserResponse
import cairn.v1.GetOverviewResponse
import cairn.v1.ListHomeFeedResponse
import cairn.v1.PageResponse
import cairn.v1.SearchActivitiesRequest
import cairn.v1.SearchActivitiesResponse
import cairn.v1.Totals
import cairn.v1.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.auth.AuthFlow
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.InMemorySecretStore
import org.opencairn.cairn.core.auth.SessionManager
import org.opencairn.cairn.core.auth.TokenResponse
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.Server
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityRepositoryImplTest {
    private val proto = headersOf(HttpHeaders.ContentType, "application/proto")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val dispatchers =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private var online = true
    private var searchCalls = mutableListOf<SearchActivitiesRequest>()

    private fun activity(
        id: String,
        title: String = "Run $id",
    ) = cairn.v1.Activity(id = id, title = title, type = cairn.v1.ActivityType.ACTIVITY_TYPE_RUN)

    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            if (!online) throw java.io.IOException("offline")
            when (path) {
                "/.well-known/oauth-authorization-server" -> {
                    respond(
                        """{"issuer":"https://c","authorization_endpoint":"https://c/a","token_endpoint":"https://c/t"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }

                "/cairn.v1.AuthService/GetCurrentUser" -> {
                    respond(
                        GetCurrentUserResponse(user = User(id = "u1", username = "jo")).encode(),
                        HttpStatusCode.OK,
                        proto,
                    )
                }

                "/cairn.v1.ActivityService/SearchActivities" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    val req = SearchActivitiesRequest.ADAPTER.decode(body)
                    searchCalls += req
                    val page1 = req.page?.cursor.isNullOrEmpty()
                    val response =
                        SearchActivitiesResponse(
                            activities = if (page1) listOf(activity("a1"), activity("a2")) else listOf(activity("a3")),
                            facets = ActivityFacets(types = listOf(Facet(value_ = "run", count = 3))),
                            total = 3,
                            matched = 3,
                            page = PageResponse(next_cursor = if (page1) "2" else ""),
                        )
                    respond(response.encode(), HttpStatusCode.OK, proto)
                }

                "/cairn.v1.ActivityService/GetActivity" -> {
                    respond(
                        GetActivityResponse(activity = activity("a1", "Morning run")).encode(),
                        HttpStatusCode.OK,
                        proto,
                    )
                }

                "/cairn.v1.StatsService/GetOverview" -> {
                    respond(
                        GetOverviewResponse(totals = Totals(count = 7, distance_m = 1000.0)).encode(),
                        HttpStatusCode.OK,
                        proto,
                    )
                }

                "/cairn.v1.SocialService/ListHomeFeed" -> {
                    respond(ListHomeFeedResponse().encode(), HttpStatusCode.OK, proto)
                }

                else -> {
                    respond("""{"code":"unimplemented","message":"$path"}""", HttpStatusCode.NotImplemented, json)
                }
            }
        }

    private class Fixture(
        val repo: ActivityRepositoryImpl,
        val overview: OverviewRepositoryImpl,
        val db: CairnDb,
    )

    private suspend fun fixture(): Fixture {
        val db = CairnDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), CairnDb.Schema))
        val platform =
            object : AuthPlatform {
                override val redirectUri = "http://127.0.0.1:8765/callback"
                override val authFlow = AuthFlow { TokenResponse("x") }
                override val secretStore = InMemorySecretStore()
            }
        val session = SessionManager(HttpClient(engine), db, platform, dispatchers, "test")
        session
            .signInWithToken(
                Server("https://c", null, federationEnabled = false, oauthEnabled = true),
                "cairn_pat_ok",
            ).getOrThrow()
        val scope = CacheScope(session)
        return Fixture(
            ActivityRepositoryImpl(db, scope, dispatchers),
            OverviewRepositoryImpl(db, scope, dispatchers),
            db,
        )
    }

    @Test
    fun searchRefreshesThenPagesAndServesFromCache() =
        runTest {
            val f = fixture()
            val filter = ActivityFilter(types = setOf(org.opencairn.cairn.core.domain.model.ActivityType.RUN))
            f.repo.observeSearch(filter, ActivitySort.DATE_DESC).test {
                assertNull(awaitItem())
                f.repo.refreshSearch(filter, ActivitySort.DATE_DESC).getOrThrow()
                val page1 = awaitItem()
                assertNotNull(page1)
                assertEquals(listOf("a1", "a2"), page1.activities.map { it.id })
                assertEquals(3, page1.matched)
                assertTrue(page1.hasMore)
                assertEquals(
                    "run",
                    page1.facets.types
                        .single()
                        .value,
                )
                assertTrue(
                    f.repo
                        .loadMoreSearch(filter, ActivitySort.DATE_DESC)
                        .getOrThrow()
                        .not(),
                )
                val page2 = awaitItem()
                assertEquals(listOf("a1", "a2", "a3"), page2!!.activities.map { it.id })
                assertFalse(page2.hasMore)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(cairn.v1.ActivityType.ACTIVITY_TYPE_RUN), searchCalls.first().filter?.types)
            // Offline: cache still answers, refresh reports the failure.
            online = false
            val result = f.repo.refreshSearch(filter, ActivitySort.DATE_DESC)
            assertIs<AppError.Network>(result.exceptionOrNull())
            f.repo.observeSearch(filter, ActivitySort.DATE_DESC).test {
                assertEquals(3, awaitItem()!!.activities.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun detailRefreshUpdatesTheCachedRow() =
        runTest {
            val f = fixture()
            f.repo.observeActivity("a1").test {
                assertTrue(awaitItem().isEmpty)
                f.repo.refreshActivity("a1").getOrThrow()
                val cached = awaitItem()
                assertEquals("Morning run", cached.value?.title)
                assertNotNull(cached.fetchedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unimplementedSecondaryDataIsReportedNotCached() =
        runTest {
            val f = fixture()
            assertIs<AppError.Unimplemented>(f.repo.refreshBestEfforts("a1").exceptionOrNull())
            f.repo.observeBestEfforts("a1").test {
                assertTrue(awaitItem().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun overviewSnapshotRoundTrips() =
        runTest {
            val f = fixture()
            f.overview.refreshOverview().getOrThrow()
            f.overview.refreshHomeFeed().getOrThrow()
            f.overview.observeOverview().test {
                val o = awaitItem()
                assertEquals(7, o.value?.totals?.count)
                assertEquals(
                    1.0,
                    o.value
                        ?.totals
                        ?.distance
                        ?.kilometers,
                )
                cancelAndIgnoreRemainingEvents()
            }
            f.overview.observeHomeFeed().test {
                assertEquals(emptyList(), awaitItem().value)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
