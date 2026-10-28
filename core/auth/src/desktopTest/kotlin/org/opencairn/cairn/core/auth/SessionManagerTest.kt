package org.opencairn.cairn.core.auth

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import cairn.v1.GetCurrentUserResponse
import cairn.v1.GetInstanceInfoResponse
import cairn.v1.UnitSystem
import cairn.v1.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.model.Units
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val BASE = "https://cairn.test"

/** A fake cairn-core: OAuth metadata, registration, token, revoke and the two auth RPCs. */
private class FakeCore(
    private val validAccessToken: () -> String,
) {
    val requests = mutableListOf<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val proto = headersOf(HttpHeaders.ContentType, "application/proto")
    private val metadata =
        """
        {"issuer":"$BASE","authorization_endpoint":"$BASE/oauth/authorize",
         "token_endpoint":"$BASE/oauth/token","revocation_endpoint":"$BASE/oauth/revoke",
         "registration_endpoint":"$BASE/oauth/register",
         "scopes_supported":["activities:read","profile:read"],
         "code_challenge_methods_supported":["S256"]}
        """.trimIndent()
    private val tokenResponse =
        """{"access_token":"cairn_at_2","token_type":"Bearer","expires_in":3600,"refresh_token":"cairn_rt_2"}"""

    fun engine() =
        MockEngine { request ->
            requests += request
            handle(request)
        }

    fun count(suffix: String) = requests.count { it.url.encodedPath.endsWith(suffix) }

    private fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/.well-known/oauth-authorization-server") -> {
                respond(metadata, HttpStatusCode.OK, json)
            }

            path.endsWith("/oauth/register") -> {
                respond("""{"client_id":"cairn_dcr_1","scope":"activities:read"}""", HttpStatusCode.Created, json)
            }

            path.endsWith("/oauth/token") -> {
                respond(tokenResponse, HttpStatusCode.OK, json)
            }

            path.endsWith("/oauth/revoke") -> {
                respond("", HttpStatusCode.OK)
            }

            path == "/cairn.v1.AuthService/GetInstanceInfo" -> {
                respond(
                    GetInstanceInfoResponse(version = "v0.6.0", oauth_enabled = true).encode(),
                    HttpStatusCode.OK,
                    proto,
                )
            }

            path == "/cairn.v1.AuthService/GetCurrentUser" -> {
                currentUser(request.headers["Authorization"])
            }

            else -> {
                respond("""{"code":"unimplemented"}""", HttpStatusCode.NotImplemented, json)
            }
        }
    }

    private fun MockRequestHandleScope.currentUser(auth: String?): HttpResponseData {
        if (auth != "Bearer ${validAccessToken()}" && auth != "Bearer cairn_pat_ok") {
            return respond("""{"code":"unauthenticated","message":"bad token"}""", HttpStatusCode.Unauthorized, json)
        }
        val user = User(id = "u1", username = "jo", display_name = "Jo", units = UnitSystem.UNIT_SYSTEM_IMPERIAL)
        return respond(
            GetCurrentUserResponse(user = user, permissions = listOf("x")).encode(),
            HttpStatusCode.OK,
            proto,
        )
    }
}

class SessionManagerTest {
    private var now = Instant.fromEpochSeconds(1_000_000)
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }
    private val dispatchers =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val server = Server(BASE, "v0.6.0", federationEnabled = false, oauthEnabled = true)
    private val browserFlow =
        AuthFlow {
            TokenResponse("cairn_at_1", expiresIn = 3600, refreshToken = "cairn_rt_1", scope = "activities:read")
        }

    private class Fixture(
        val core: FakeCore,
        val db: CairnDb,
        val secrets: InMemorySecretStore,
        val session: SessionManager,
    )

    private fun fixture(validToken: (FakeCore) -> String = { "cairn_at_1" }): Fixture {
        lateinit var core: FakeCore
        core = FakeCore { validToken(core) }
        val db = CairnDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), CairnDb.Schema))
        val secrets = InMemorySecretStore()
        val platform =
            object : AuthPlatform {
                override val redirectUri = "http://127.0.0.1:8765/callback"
                override val authFlow = browserFlow
                override val secretStore = secrets
            }
        val session = SessionManager(HttpClient(core.engine()), db, platform, dispatchers, "0.1.0-test", clock)
        return Fixture(core, db, secrets, session)
    }

    @Test
    fun discoverReadsMetadataAndInstanceInfo() =
        runTest {
            val f = fixture()
            val discovered = f.session.discoverServer("cairn.test/").getOrThrow()
            assertEquals(BASE, discovered.baseUrl)
            assertEquals("v0.6.0", discovered.version)
            assertTrue(discovered.oauthEnabled)
            assertNotNull(
                f.db.serversQueries
                    .byUrl(BASE)
                    .executeAsOneOrNull(),
            )
        }

    @Test
    fun browserSignInRegistersClientOnceAndStoresTokens() =
        runTest {
            val f = fixture()
            val user = f.session.signIn(server).getOrThrow()
            assertEquals("jo", user.username)
            assertEquals(Units.IMPERIAL, user.preferences.units)
            assertEquals(
                "cairn_dcr_1",
                f.db.serversQueries
                    .byUrl(BASE)
                    .executeAsOne()
                    .oauth_client_id,
            )
            f.session.currentUser.test {
                assertEquals("u1", awaitItem()?.id)
                cancelAndIgnoreRemainingEvents()
            }
            f.session.signIn(server).getOrThrow()
            assertEquals(1, f.core.count("/oauth/register"))
            val registration = f.core.requests.first { it.url.encodedPath.endsWith("/oauth/register") }
            val body = (registration.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("\"software_id\":\"org.opencairn.cairn\""), body)
            assertTrue(body.contains("\"token_endpoint_auth_method\":\"none\""), body)
        }

    @Test
    fun patSignInRejectedByServerLeavesNoAccount() =
        runTest {
            val f = fixture()
            val result = f.session.signInWithToken(server, "cairn_pat_wrong")
            assertIs<AppError.Unauthenticated>(result.exceptionOrNull())
            assertNull(
                f.db.accountsQueries
                    .active()
                    .executeAsOneOrNull(),
            )
            assertNull(f.secrets.get("tokens:" + AccountKey(BASE, "pending").storageId))
        }

    @Test
    fun expiringAccessTokenIsRefreshedBeforeUse() =
        runTest {
            val f = fixture { core -> if (core.count("/oauth/token") > 0) "cairn_at_2" else "cairn_at_1" }
            f.session.signIn(server).getOrThrow()
            now = Instant.fromEpochSeconds(1_000_000 + 3600 - 10)
            assertEquals("Bearer cairn_at_2", f.session.headers()["Authorization"])
            assertEquals(1, f.core.count("/oauth/token"))
            f.session.refreshCurrentUser().getOrThrow()
            assertEquals(1, f.core.count("/oauth/token"))
        }

    @Test
    fun signOutRevokesAndForgets() =
        runTest {
            val f = fixture()
            f.session.signIn(server).getOrThrow()
            f.session.signOut()
            assertEquals(1, f.core.count("/oauth/revoke"))
            assertNull(
                f.db.accountsQueries
                    .active()
                    .executeAsOneOrNull(),
            )
            assertTrue(f.session.headers().isEmpty())
        }
}
