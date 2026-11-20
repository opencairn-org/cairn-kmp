package org.opencairn.cairn.core.network.connect

import cairn.v1.ActivityEvent
import cairn.v1.ActivityEventType
import cairn.v1.StreamActivityEventsRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.runTest
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.network.CairnApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectServerStreamTest {
    private val streamHeaders = headersOf(HttpHeaders.ContentType, "application/connect+proto")

    private fun api(
        authenticator: RequestAuthenticator = NoAuthenticator,
        handler: MockRequestHandler,
    ): CairnApi = CairnApi.connect(HttpClient(MockEngine(handler)), "https://cairn.test", authenticator)

    private fun event(id: String) =
        ActivityEvent(type = ActivityEventType.ACTIVITY_EVENT_TYPE_ACTIVITY_CREATED, activity_id = id)

    private fun frames(
        vararg events: ActivityEvent,
        end: String? = "{}",
    ): ByteArray =
        events.fold(ByteArray(0)) { acc, e -> acc + envelope(e.encode()) } +
            (end?.let { envelope(it.encodeToByteArray(), FLAG_END_STREAM) } ?: ByteArray(0))

    @Test
    fun envelopeCarriesFlagsAndBigEndianLength() {
        val framed = envelope(ByteArray(258) { 7 }, FLAG_END_STREAM)
        assertEquals(listOf<Byte>(2, 0, 0, 1, 2), framed.take(5))
        assertEquals(263, framed.size)
    }

    @Test
    fun streamPostsOneEnvelopeAndDeliversEveryMessage() =
        runTest {
            var contentType = ""
            var requestBody = ByteArray(0)
            val api =
                api { request ->
                    contentType = request.body.contentType.toString()
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respond(frames(event("a1"), event("a2")), HttpStatusCode.OK, streamHeaders)
                }
            val request = StreamActivityEventsRequest(types = listOf(ActivityEventType.ACTIVITY_EVENT_TYPE_NEW_PR))
            val events =
                api.activities
                    .StreamActivityEvents()
                    .executeIn(this, request)
                    .toList()
            assertEquals(listOf("a1", "a2"), events.map { it.activity_id })
            assertEquals("application/connect+proto", contentType)
            assertEquals(envelope(request.encode()).toList(), requestBody.toList())
        }

    @Test
    fun endOfStreamErrorClosesTheChannelAfterTheMessages() =
        runTest {
            val end = """{"error":{"code":"permission_denied","message":"token lacks the activities:read scope"}}"""
            val api = api { respond(frames(event("a1"), end = end), HttpStatusCode.OK, streamHeaders) }
            val channel = api.activities.StreamActivityEvents().executeIn(this, StreamActivityEventsRequest())
            assertEquals("a1", channel.receive().activity_id)
            val e = assertFailsWith<ConnectException> { channel.receive() }
            assertEquals(ConnectCode.PERMISSION_DENIED, e.code)
            assertIs<AppError.PermissionDenied>(e.toAppError())
        }

    @Test
    fun streamThatBreaksOffIsUnavailable() =
        runTest {
            val api = api { respond(frames(event("a1"), end = null), HttpStatusCode.OK, streamHeaders) }
            val channel = api.activities.StreamActivityEvents().executeIn(this, StreamActivityEventsRequest())
            assertEquals("a1", channel.receive().activity_id)
            val e = assertFailsWith<ConnectException> { channel.receive() }
            assertEquals(ConnectCode.UNAVAILABLE, e.code)
        }

    @Test
    fun rejectedTokenIsRefreshedOnceThenTheStreamRuns() =
        runTest {
            var calls = 0
            var refreshed = 0
            val authenticator =
                object : RequestAuthenticator {
                    override suspend fun headers() = mapOf("Authorization" to "Bearer t$refreshed")

                    override suspend fun onUnauthenticated(): Boolean {
                        refreshed++
                        return true
                    }
                }
            val api =
                api(authenticator) { request ->
                    calls++
                    if (request.headers["Authorization"] == "Bearer t0") {
                        val end = """{"error":{"code":"unauthenticated","message":"token expired"}}"""
                        respond(frames(end = end), HttpStatusCode.OK, streamHeaders)
                    } else {
                        respond(frames(event("a1")), HttpStatusCode.OK, streamHeaders)
                    }
                }
            val events =
                api.activities
                    .StreamActivityEvents()
                    .executeIn(this, StreamActivityEventsRequest())
                    .toList()
            assertEquals(listOf("a1"), events.map { it.activity_id })
            assertEquals(2, calls)
            assertEquals(1, refreshed)
        }

    @Test
    fun plainHttpErrorIsMappedLikeAUnaryCall() =
        runTest {
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val body = """{"code":"unimplemented","message":"no"}"""
            val api = api { respond(body, HttpStatusCode.NotImplemented, json) }
            val channel = api.activities.StreamActivityEvents().executeIn(this, StreamActivityEventsRequest())
            val e = assertFailsWith<ConnectException> { channel.receive() }
            assertEquals(ConnectCode.UNIMPLEMENTED, e.code)
            assertTrue(channel.receiveCatching().isClosed)
        }
}
