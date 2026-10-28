package org.opencairn.cairn.core.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TokenSetTest {
    @Test
    fun patNeverExpires() {
        assertFalse(TokenSet(AuthKind.PAT, "cairn_pat_x").expiresWithin(60, Instant.fromEpochSeconds(1_000_000)))
    }

    @Test
    fun oauthExpiryWindow() {
        val t = TokenSet(AuthKind.OAUTH, "a", "r", expiresAtEpochSeconds = 1_000)
        assertTrue(t.expiresWithin(60, Instant.fromEpochSeconds(950)))
        assertFalse(t.expiresWithin(60, Instant.fromEpochSeconds(900)))
    }
}
