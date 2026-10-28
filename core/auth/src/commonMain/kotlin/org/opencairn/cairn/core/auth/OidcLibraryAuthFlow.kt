package org.opencairn.cairn.core.auth

import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.types.CodeChallengeMethod

/**
 * [AuthFlow] over kotlin-multiplatform-oidc: Custom Tabs on Android, ASWebAuthenticationSession
 * on iOS, a loopback web server on desktop. cairn-core is plain OAuth 2.1 (no id_token), which the
 * library supports with explicit endpoints and `disableNonce`.
 */
class OidcLibraryAuthFlow(
    private val factory: CodeAuthFlowFactory,
) : AuthFlow {
    override suspend fun authorize(config: AuthorizeConfig): TokenResponse {
        val client =
            OpenIdConnectClient {
                endpoints {
                    authorizationEndpoint = config.authorizationEndpoint
                    tokenEndpoint = config.tokenEndpoint
                }
                clientId = config.clientId
                scope = config.scope
                codeChallengeMethod = CodeChallengeMethod.S256
                redirectUri = config.redirectUri
                disableNonce = true
            }
        val tokens = factory.createAuthFlow(client).getAccessToken()
        return TokenResponse(
            accessToken = tokens.access_token,
            tokenType = tokens.token_type ?: "Bearer",
            expiresIn = tokens.expires_in?.toLong(),
            refreshToken = tokens.refresh_token,
            scope = tokens.scope,
        )
    }
}
