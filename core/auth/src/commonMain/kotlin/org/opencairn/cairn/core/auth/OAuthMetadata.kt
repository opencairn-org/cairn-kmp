package org.opencairn.cairn.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opencairn.cairn.core.common.AppError

/** RFC 8414 authorization-server metadata, the subset the app uses. */
@Serializable
data class OAuthMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported") val codeChallengeMethods: List<String> = emptyList(),
)

@Serializable
data class ClientRegistrationRequest(
    @SerialName("client_name") val clientName: String,
    @SerialName("redirect_uris") val redirectUris: List<String>,
    @SerialName("grant_types") val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    val scope: String,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String = "none",
    @SerialName("software_id") val softwareId: String,
    @SerialName("software_version") val softwareVersion: String,
)

@Serializable
data class ClientRegistrationResponse(
    @SerialName("client_id") val clientId: String,
    val scope: String = "",
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

@Serializable
data class OAuthErrorBody(
    val error: String = "",
    @SerialName("error_description") val description: String = "",
)

/** Thin HTTP client for the OAuth 2.1 endpoints of a cairn-core instance (protocol, not API). */
class OAuthHttp(
    private val http: HttpClient,
) {
    // encodeDefaults: the registration request states grant types and auth method explicitly (RFC 7591).
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun metadata(baseUrl: String): OAuthMetadata {
        val response = http.get("${baseUrl.trimEnd('/')}/.well-known/oauth-authorization-server")
        if (!response.status.isSuccess()) throw AppError.Server("no OAuth metadata (HTTP ${response.status.value})")
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun register(
        endpoint: String,
        request: ClientRegistrationRequest,
    ): ClientRegistrationResponse {
        val response =
            http.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ClientRegistrationRequest.serializer(), request))
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw oauthError(text, "client registration failed")
        return json.decodeFromString(text)
    }

    suspend fun refresh(
        tokenEndpoint: String,
        clientId: String,
        refreshToken: String,
    ): TokenResponse {
        val response =
            http.submitForm(
                url = tokenEndpoint,
                formParameters =
                    parameters {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", clientId)
                    },
            )
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = parseError(text)
            if (err?.error == "invalid_grant") throw AppError.Unauthenticated("session expired")
            throw oauthError(text, "token refresh failed")
        }
        return json.decodeFromString(text)
    }

    suspend fun revoke(
        revocationEndpoint: String,
        clientId: String,
        token: String,
    ) {
        http.submitForm(
            url = revocationEndpoint,
            formParameters =
                parameters {
                    append("token", token)
                    append("token_type_hint", "refresh_token")
                    append("client_id", clientId)
                },
        )
    }

    private fun parseError(text: String): OAuthErrorBody? =
        runCatching {
            json.decodeFromString<OAuthErrorBody>(text)
        }.getOrNull()

    private fun oauthError(
        text: String,
        fallback: String,
    ): AppError {
        val err = parseError(text) ?: return AppError.Server(fallback)
        val message = "${err.error}: ${err.description}".trim(':', ' ')
        return AppError.Server(message.ifBlank { fallback })
    }
}
