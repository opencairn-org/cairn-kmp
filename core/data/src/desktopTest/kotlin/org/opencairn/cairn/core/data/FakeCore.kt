package org.opencairn.cairn.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cairn.v1.GetCurrentUserResponse
import cairn.v1.User
import com.squareup.wire.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.opencairn.cairn.core.auth.AuthFlow
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.InMemorySecretStore
import org.opencairn.cairn.core.auth.SessionManager
import org.opencairn.cairn.core.auth.TokenResponse
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.Server
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/** What a route answers with. */
internal sealed interface Reply {
    class Ok(
        val message: Message<*, *>,
    ) : Reply

    /** A Connect error, e.g. `unimplemented` with 501. */
    class Failure(
        val code: String,
        val status: HttpStatusCode,
    ) : Reply

    /** Raw bytes of an enveloped stream. */
    class Stream(
        val frames: ByteArray,
    ) : Reply

    class Text(
        val body: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    ) : Reply
}

/**
 * A scripted cairn-core behind a MockEngine: tests register one answer per procedure (or URL path)
 * and get repositories wired to a signed-in session over an in-memory database.
 */
internal class FakeCore {
    val dispatchers =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
        }
    val routes = mutableMapOf<String, (ByteArray) -> Reply>()
    val calls = CopyOnWriteArrayList<String>()
    var online = true

    val http =
        HttpClient(
            MockEngine { request ->
                if (!online) throw java.io.IOException("offline")
                val path = request.url.encodedPath
                calls += path.substringAfterLast('/')
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
                answer(routes[path]?.invoke(body) ?: builtIn(path))
            },
        )
    val db = CairnDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), CairnDb.Schema))
    lateinit var scope: CacheScope
        private set
    val snapshots by lazy { SnapshotStore(db, scope, dispatchers) }

    /** Registers the answer of `cairn.v1.<service>/<method>`. */
    fun on(
        procedure: String,
        reply: (ByteArray) -> Reply,
    ) {
        routes["/cairn.v1.$procedure"] = reply
    }

    fun count(method: String): Int = calls.count { it == method }

    suspend fun signIn(): FakeCore {
        val platform =
            object : AuthPlatform {
                override val redirectUri = "http://127.0.0.1:8765/callback"
                override val authFlow = AuthFlow { TokenResponse("x") }
                override val secretStore = InMemorySecretStore()
            }
        val session = SessionManager(http, db, platform, dispatchers, "test")
        session
            .signInWithToken(Server("https://c", null, federationEnabled = false, oauthEnabled = true), "cairn_pat_ok")
            .getOrThrow()
        scope = CacheScope(session)
        return this
    }

    private fun builtIn(path: String): Reply =
        when (path) {
            "/.well-known/oauth-authorization-server" -> {
                Reply.Text(
                    """{"issuer":"https://c","authorization_endpoint":"https://c/a","token_endpoint":"https://c/t"}""",
                )
            }

            "/cairn.v1.AuthService/GetCurrentUser" -> {
                Reply.Ok(GetCurrentUserResponse(user = User(id = "u1", username = "jo")))
            }

            else -> {
                Reply.Failure("unimplemented", HttpStatusCode.NotImplemented)
            }
        }

    private fun MockRequestHandleScope.answer(reply: Reply): HttpResponseData =
        when (reply) {
            is Reply.Ok -> {
                respond(reply.message.encode(), HttpStatusCode.OK, type("application/proto"))
            }

            is Reply.Failure -> {
                respond("""{"code":"${reply.code}","message":"scripted"}""", reply.status, type("application/json"))
            }

            is Reply.Stream -> {
                respond(reply.frames, HttpStatusCode.OK, type("application/connect+proto"))
            }

            is Reply.Text -> {
                respond(reply.body, reply.status, type("application/json"))
            }
        }

    private fun type(value: String) = headersOf(HttpHeaders.ContentType, value)

    companion object {
        /** One message of a Connect stream; `end` marks the closing envelope. */
        fun frame(
            payload: ByteArray,
            end: Boolean = false,
        ): ByteArray {
            val head = byteArrayOf(if (end) 2 else 0, 0, 0, (payload.size ushr 8).toByte(), payload.size.toByte())
            return head + payload
        }
    }
}
