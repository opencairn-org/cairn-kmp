package org.opencairn.cairn.core.auth

/** Everything a browser-based authorization needs; filled from metadata + registration. */
data class AuthorizeConfig(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scope: String,
    val redirectUri: String,
)

/** Runs the authorization-code + PKCE flow in the system browser and returns the tokens. */
fun interface AuthFlow {
    suspend fun authorize(config: AuthorizeConfig): TokenResponse
}

/** Platform wiring for sign-in: the browser flow and the redirect URI it listens on. */
interface AuthPlatform {
    val redirectUri: String
    val authFlow: AuthFlow
    val secretStore: SecretStore
}
