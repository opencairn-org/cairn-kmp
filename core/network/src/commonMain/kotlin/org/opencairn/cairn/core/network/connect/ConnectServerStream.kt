package org.opencairn.cairn.core.network.connect

import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.MessageSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import okio.Timeout
import kotlin.concurrent.Volatile

internal const val FLAG_COMPRESSED = 0x01
internal const val FLAG_END_STREAM = 0x02

/** The server sends a heartbeat every 30 s; silence for longer than this means the connection is dead. */
private const val IDLE_TIMEOUT_MS = 90_000L

/** Frames one message for a Connect stream: a flags byte, the big-endian length, the payload. */
internal fun envelope(
    payload: ByteArray,
    flags: Int = 0,
): ByteArray {
    val out = ByteArray(payload.size + 5)
    out[0] = flags.toByte()
    out[1] = (payload.size ushr 24).toByte()
    out[2] = (payload.size ushr 16).toByte()
    out[3] = (payload.size ushr 8).toByte()
    out[4] = payload.size.toByte()
    payload.copyInto(out, 5)
    return out
}

/**
 * One server-streaming RPC over the Connect protocol: the request is a single enveloped message
 * posted as `application/connect+proto`, the response a sequence of enveloped messages that ends
 * with an end-of-stream envelope carrying the status as JSON. Failures close the channel with a
 * [ConnectException]; a stream that breaks off is reported as `unavailable`.
 */
internal class ConnectServerStreamingCall<S : Any, R : Any>(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authenticator: RequestAuthenticator,
    override val method: GrpcMethod<S, R>,
) : GrpcServerStreamingCall<S, R> {
    override val timeout: Timeout = Timeout.NONE
    override var requestMetadata: Map<String, String> = emptyMap()

    @Volatile override var responseMetadata: Map<String, String>? = null
        private set
    private var job: Job? = null
    private var executed = false
    private var canceled = false

    override fun cancel() {
        canceled = true
        job?.cancel()
    }

    override fun isCanceled(): Boolean = canceled

    override fun isExecuted(): Boolean = executed

    override fun clone(): GrpcServerStreamingCall<S, R> =
        ConnectServerStreamingCall(httpClient, baseUrl, authenticator, method)

    override fun executeBlocking(request: S): MessageSource<R> = throw UnsupportedOperationException("use executeIn()")

    override suspend fun executeIn(
        scope: CoroutineScope,
        request: S,
    ): ReceiveChannel<R> {
        check(!executed) { "already executed" }
        executed = true
        val out = Channel<R>(Channel.BUFFERED)
        job =
            scope.launch {
                try {
                    stream(request, out, retryOnUnauthenticated = true)
                    out.close()
                } catch (e: CancellationException) {
                    out.cancel(e)
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    out.close(e.asStreamFailure())
                }
            }
        return out
    }

    private suspend fun stream(
        request: S,
        out: SendChannel<R>,
        retryOnUnauthenticated: Boolean,
    ) {
        val body = envelope(method.requestAdapter.encode(request))
        val headers = authenticator.headers()
        val failure =
            httpClient
                .preparePost("$baseUrl${method.path}") {
                    contentType(ContentType("application", "connect+proto"))
                    header("Connect-Protocol-Version", "1")
                    requestMetadata.forEach { (k, v) -> header(k, v) }
                    headers.forEach { (k, v) -> header(k, v) }
                    // The stream stays open for as long as the caller listens.
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = IDLE_TIMEOUT_MS
                    }
                    setBody(body)
                }.execute { response ->
                    responseMetadata =
                        response.headers
                            .entries()
                            .associate { (k, v) -> k.lowercase() to v.joinToString(",") }
                    if (response.status != HttpStatusCode.OK) {
                        response.toConnectException(method.path)
                    } else {
                        pump(response.bodyAsChannel(), out)
                    }
                }
        if (failure == null) return
        // A rejected token is reported before the first message; one retry with fresh headers.
        val retry = failure.code == ConnectCode.UNAUTHENTICATED && retryOnUnauthenticated
        if (retry && authenticator.onUnauthenticated()) {
            stream(request, out, retryOnUnauthenticated = false)
        } else {
            throw failure
        }
    }

    /** Forwards messages until the end-of-stream envelope; returns the error that envelope carried, if any. */
    private suspend fun pump(
        input: ByteReadChannel,
        out: SendChannel<R>,
    ): ConnectException? {
        while (true) {
            val flags = input.readByte().toInt()
            val length = input.readInt()
            val payload = input.readByteArray(length)
            if (flags and FLAG_END_STREAM != 0) return endOfStreamError(payload)
            if (flags and FLAG_COMPRESSED != 0) {
                throw ConnectException(
                    ConnectCode.INTERNAL,
                    "compressed message without negotiated encoding",
                    method.path,
                )
            }
            out.send(method.responseAdapter.decode(payload))
        }
    }

    private fun endOfStreamError(payload: ByteArray): ConnectException? {
        if (payload.isEmpty()) return null
        val error =
            connectJson
                .parseToJsonElement(payload.decodeToString())
                .jsonObject["error"]
                ?.jsonObject ?: return null
        return error.toConnectException(ConnectCode.UNKNOWN, method.path)
    }

    private fun Exception.asStreamFailure(): ConnectException =
        this as? ConnectException
            ?: ConnectException(ConnectCode.UNAVAILABLE, message ?: "stream interrupted", method.path, cause = this)
}
