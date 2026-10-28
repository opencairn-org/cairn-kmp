package org.opencairn.cairn.core.auth

import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class AuthKind { OAUTH, PAT }

/** What the app holds for one account. Persisted only through [SecretStore]. */
@Serializable
data class TokenSet(
    val kind: AuthKind,
    val accessToken: String,
    val refreshToken: String? = null,
    /** Epoch seconds; null = does not expire (PAT). */
    val expiresAtEpochSeconds: Long? = null,
    val scope: String = "",
    val clientId: String? = null,
) {
    fun expiresWithin(
        seconds: Long,
        now: Instant,
    ): Boolean {
        val exp = expiresAtEpochSeconds ?: return false
        return exp - now.epochSeconds <= seconds
    }
}
