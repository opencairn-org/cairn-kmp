package org.opencairn.cairn.core.network.connect

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcClientStreamingCall
import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.GrpcStreamingCall
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.IOException
import okio.Timeout
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Supplies per-request headers (bearer token) and reacts to 401s. */
interface RequestAuthenticator {
    suspend fun headers(): Map<String, String>

    /** Called once on `unauthenticated`; return true when a retry with fresh headers is worth it. */
    suspend fun onUnauthenticated(): Boolean
}

object NoAuthenticator : RequestAuthenticator {
    override suspend fun headers(): Map<String, String> = emptyMap()

    override suspend fun onUnauthenticated(): Boolean = false
}

/**
 * Wire [GrpcClient] speaking the Connect protocol (unary) over Ktor:
 * `POST <baseUrl>/<package>.<Service>/<Method>` with `application/proto` bodies,
 * `Connect-Protocol-Version: 1`, JSON error bodies mapped to [ConnectException].
 *
 * Server streams arrive with handover group H4; [newServerStreamingCall] throws until then.
 */
class ConnectGrpcClient(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val authenticator: RequestAuthenticator = NoAuthenticator,
    private val defaultTimeout: Duration = 30.seconds,
) : GrpcClient() {
    private val baseUrl = baseUrl.trimEnd('/')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun <S : Any, R : Any> newCall(method: GrpcMethod<S, R>): GrpcCall<S, R> = ConnectUnaryCall(method)

    override fun <S : Any, R : Any> newStreamingCall(method: GrpcMethod<S, R>): GrpcStreamingCall<S, R> =
        throw UnsupportedOperationException("bidi streams are not part of the Connect client")

    override fun <S : Any, R : Any> newClientStreamingCall(method: GrpcMethod<S, R>): GrpcClientStreamingCall<S, R> =
        throw UnsupportedOperationException("client streams are not part of the Connect client")

    override fun <S : Any, R : Any> newServerStreamingCall(method: GrpcMethod<S, R>): GrpcServerStreamingCall<S, R> =
        throw UnsupportedOperationException("server streams arrive with handover H4")

    /** One unary RPC. Wire's generated clients call [execute] from `suspend` code. */
    private inner class ConnectUnaryCall<S : Any, R : Any>(
        override val method: GrpcMethod<S, R>,
    ) : GrpcCall<S, R> {
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

        override fun clone(): GrpcCall<S, R> = ConnectUnaryCall(method)

        @Suppress("TooGenericExceptionCaught")
        override suspend fun execute(request: S): R {
            check(!executed) { "already executed" }
            executed = true
            return try {
                unary(request, retryOnUnauthenticated = true)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                throw e.asConnectException(method.path)
            }
        }

        override fun executeBlocking(request: S): R = throw UnsupportedOperationException("use execute()")

        override fun enqueue(
            request: S,
            callback: GrpcCall.Callback<S, R>,
        ) {
            job =
                scope.launch {
                    try {
                        callback.onSuccess(this@ConnectUnaryCall, execute(request))
                    } catch (e: ConnectException) {
                        callback.onFailure(this@ConnectUnaryCall, IOException(e.message, e))
                    }
                }
        }

        private suspend fun unary(
            request: S,
            retryOnUnauthenticated: Boolean,
        ): R {
            val body = method.requestAdapter.encode(request)
            // Deadlines: the server honors Connect-Timeout-Ms; the HttpClient's HttpTimeout plugin bounds the call.
            val response =
                httpClient.post("$baseUrl${method.path}") {
                    contentType(ContentType("application", "proto"))
                    header("Connect-Protocol-Version", "1")
                    header("Connect-Timeout-Ms", defaultTimeout.inWholeMilliseconds.toString())
                    requestMetadata.forEach { (k, v) -> header(k, v) }
                    authenticator.headers().forEach { (k, v) -> header(k, v) }
                    setBody(body)
                }
            responseMetadata =
                response.headers
                    .entries()
                    .associate { (k, v) -> k.lowercase() to v.joinToString(",") }
            if (response.status == HttpStatusCode.OK) {
                return method.responseAdapter.decode(response.bodyAsBytes())
            }
            val error = parseError(response)
            val retry = error.code == ConnectCode.UNAUTHENTICATED && retryOnUnauthenticated
            if (retry && authenticator.onUnauthenticated()) {
                return unary(request, retryOnUnauthenticated = false)
            }
            throw error
        }

        private suspend fun parseError(response: HttpResponse): ConnectException {
            val fallback = ConnectCode.fromHttpStatus(response.status.value)
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            val isJson = response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true
            if (!isJson || text.isBlank()) {
                return ConnectException(fallback, "HTTP ${response.status.value}", method.path)
            }
            return runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                val code = obj["code"]?.jsonPrimitive?.content?.let(ConnectCode::fromWire) ?: fallback
                val message = obj["message"]?.jsonPrimitive?.content ?: ""
                ConnectException(code, message, method.path, details = parseDetails(obj))
            }.getOrElse { ConnectException(fallback, "HTTP ${response.status.value}", method.path) }
        }

        private fun parseDetails(obj: JsonObject): List<ConnectErrorDetail> =
            obj["details"]?.jsonArray?.mapNotNull { d ->
                val o = d.jsonObject
                val type = o["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val value = o["value"]?.jsonPrimitive?.content ?: ""
                val bytes = runCatching { Base64.Default.decode(value) }.getOrDefault(ByteArray(0))
                ConnectErrorDetail(type, bytes)
            } ?: emptyList()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Transport failures become `unavailable`, anything else `unknown`; Connect errors pass through. */
        fun Exception.asConnectException(path: String): ConnectException =
            when (this) {
                is ConnectException -> {
                    this
                }

                is IOException -> {
                    ConnectException(ConnectCode.UNAVAILABLE, message ?: "network error", path, cause = this)
                }

                else -> {
                    ConnectException(ConnectCode.UNKNOWN, message ?: "unexpected error", path, cause = this)
                }
            }
    }
}
