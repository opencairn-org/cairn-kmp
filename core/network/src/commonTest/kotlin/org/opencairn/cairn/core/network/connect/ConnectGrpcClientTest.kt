package org.opencairn.cairn.core.network.connect

import cairn.v1.GetCurrentUserRequest
import cairn.v1.GetCurrentUserResponse
import cairn.v1.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.network.CairnApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectGrpcClientTest {
    private fun api(
        authenticator: RequestAuthenticator = NoAuthenticator,
        handler: MockRequestHandler,
    ): CairnApi = CairnApi.connect(HttpClient(MockEngine(handler)), "https://cairn.test/", authenticator)

    @Test
    fun unaryCallPostsProtoAndDecodesResponse() =
        runTest {
            var seenPath = ""
            var seenContentType = ""
            var seenVersion = ""
            val api =
                api { request ->
                    seenPath = request.url.encodedPath
                    seenContentType = request.body.contentType.toString()
                    seenVersion = request.headers["Connect-Protocol-Version"] ?: ""
                    val body =
                        GetCurrentUserResponse(user = User(id = "u1", username = "jo"), permissions = listOf("admin"))
                            .encode()
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/proto"))
                }
            val response = api.auth.GetCurrentUser().execute(GetCurrentUserRequest())
            assertEquals("/cairn.v1.AuthService/GetCurrentUser", seenPath)
            assertEquals("application/proto", seenContentType)
            assertEquals("1", seenVersion)
            assertEquals("jo", response.user?.username)
            assertEquals(listOf("admin"), response.permissions)
        }

    @Test
    fun jsonErrorBodyBecomesConnectException() =
        runTest {
            val api =
                api {
                    respond(
                        """{"code":"unimplemented","message":"GetInstanceInfo is not implemented"}""",
                        HttpStatusCode.NotImplemented,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val e = assertFailsWith<ConnectException> { api.auth.GetCurrentUser().execute(GetCurrentUserRequest()) }
            assertEquals(ConnectCode.UNIMPLEMENTED, e.code)
            assertEquals("/cairn.v1.AuthService/GetCurrentUser", e.procedure)
            assertIs<AppError.Unimplemented>(e.toAppError())
        }

    @Test
    fun missingErrorBodyFallsBackToHttpStatus() =
        runTest {
            val api = api { respond("", HttpStatusCode.Unauthorized) }
            val e = assertFailsWith<ConnectException> { api.auth.GetCurrentUser().execute(GetCurrentUserRequest()) }
            assertEquals(ConnectCode.UNAUTHENTICATED, e.code)
            assertIs<AppError.Unauthenticated>(e.toAppError())
        }

    @Test
    fun unauthenticatedIsRetriedOnceAfterRefresh() =
        runTest {
            var calls = 0
            var refreshed = false
            val authenticator =
                object : RequestAuthenticator {
                    override suspend fun headers(): Map<String, String> {
                        val token = if (refreshed) "new" else "old"
                        return mapOf("Authorization" to "Bearer $token")
                    }

                    override suspend fun onUnauthenticated(): Boolean {
                        refreshed = true
                        return true
                    }
                }
            val api =
                api(authenticator) { request ->
                    calls++
                    if (request.headers["Authorization"] == "Bearer new") {
                        respond(
                            GetCurrentUserResponse(user = User(id = "u1")).encode(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/proto"),
                        )
                    } else {
                        respond(
                            """{"code":"unauthenticated"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val response = api.auth.GetCurrentUser().execute(GetCurrentUserRequest())
            assertEquals("u1", response.user?.id)
            assertEquals(2, calls)
            assertTrue(refreshed)
        }
}
