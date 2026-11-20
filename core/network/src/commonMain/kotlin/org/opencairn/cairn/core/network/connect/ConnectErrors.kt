package org.opencairn.cairn.core.network.connect

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal val connectJson = Json { ignoreUnknownKeys = true }

/** The error of a non-200 response: the Connect JSON body when there is one, else the HTTP status mapping. */
internal suspend fun HttpResponse.toConnectException(procedure: String): ConnectException {
    val fallback = ConnectCode.fromHttpStatus(status.value)
    val text = runCatching { bodyAsText() }.getOrDefault("")
    val isJson = headers[HttpHeaders.ContentType]?.startsWith("application/json") == true
    if (!isJson || text.isBlank()) {
        return ConnectException(fallback, "HTTP ${status.value}", procedure)
    }
    return runCatching { connectJson.parseToJsonElement(text).jsonObject.toConnectException(fallback, procedure) }
        .getOrElse { ConnectException(fallback, "HTTP ${status.value}", procedure) }
}

/** A Connect error object: `{"code": "...", "message": "...", "details": [...]}`. */
internal fun JsonObject.toConnectException(
    fallback: ConnectCode,
    procedure: String,
): ConnectException {
    val code = this["code"]?.jsonPrimitive?.content?.let(ConnectCode::fromWire) ?: fallback
    val message = this["message"]?.jsonPrimitive?.content ?: ""
    return ConnectException(code, message, procedure, details = errorDetails())
}

private fun JsonObject.errorDetails(): List<ConnectErrorDetail> =
    this["details"]?.jsonArray?.mapNotNull { d ->
        val o = d.jsonObject
        val type = o["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val value = o["value"]?.jsonPrimitive?.content ?: ""
        val bytes = runCatching { Base64.Default.decode(value) }.getOrDefault(ByteArray(0))
        ConnectErrorDetail(type, bytes)
    } ?: emptyList()
