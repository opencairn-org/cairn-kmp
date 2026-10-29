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
    @SerialName("device_authorization_endpoint") val deviceAuthorizationEndpoint: String? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported") val codeChallengeMethods: List<String> = emptyList(),
)

@Serializable
data class ClientRegistrationRequest(
    @SerialName("client_name") val clientName: String,
    @SerialName("redirect_uris") val redirectUris: List<String>,
    @SerialName("grant_types") val grantTypes: List<String> =
        listOf("authorization_code", "refresh_token", DEVICE_CODE_GRANT),
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

/** RFC 8628 §3.2: what the device shows and polls with. */
@Serializable
data class DeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    val interval: Long = 5,
)

/** One poll of the token endpoint during a device sign-in. */
sealed interface DevicePoll {
    data class Tokens(
        val tokens: TokenResponse,
    ) : DevicePoll

    data object Pending : DevicePoll

    /** Polling too fast: back off by five seconds (RFC 8628 §3.5). */
    data object SlowDown : DevicePoll

    data object Denied : DevicePoll

    data object Expired : DevicePoll
}

/** An OAuth error response (`error` + `error_description`) the caller wants to tell apart. */
class OAuthRequestException(
    val error: String,
    val description: String,
) : Exception("$error: $description".trim(':', ' '))

const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

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

    /** Starts a device sign-in. Throws [OAuthRequestException] on an OAuth error so callers can react to its code. */
    suspend fun deviceAuthorize(
        endpoint: String,
        clientId: String,
        scope: String,
    ): DeviceAuthorizationResponse {
        val response =
            http.submitForm(
                url = endpoint,
                formParameters =
                    parameters {
                        append("client_id", clientId)
                        append("scope", scope)
                    },
            )
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = parseError(text) ?: throw AppError.Server("device sign-in failed")
            throw OAuthRequestException(err.error, err.description)
        }
        return json.decodeFromString(text)
    }

    suspend fun pollDevice(
        tokenEndpoint: String,
        clientId: String,
        deviceCode: String,
    ): DevicePoll {
        val response =
            http.submitForm(
                url = tokenEndpoint,
                formParameters =
                    parameters {
                        append("grant_type", DEVICE_CODE_GRANT)
                        append("device_code", deviceCode)
                        append("client_id", clientId)
                    },
            )
        val text = response.bodyAsText()
        if (response.status.isSuccess()) return DevicePoll.Tokens(json.decodeFromString(text))
        return when (parseError(text)?.error) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "access_denied" -> DevicePoll.Denied
            "expired_token" -> DevicePoll.Expired
            else -> throw oauthError(text, "device sign-in failed")
        }
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
