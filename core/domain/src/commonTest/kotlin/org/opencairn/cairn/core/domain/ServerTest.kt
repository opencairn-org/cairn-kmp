package org.opencairn.cairn.core.domain

import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.Server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ServerTest {
    @Test
    fun hostStripsSchemeAndSlash() {
        val s = Server("https://cairn.example.org/", version = null, federationEnabled = false, oauthEnabled = true)
        assertEquals("cairn.example.org", s.host)
    }

    @Test
    fun storageIdIsStableAndDistinct() {
        val a = AccountKey("https://a.example", "u1")
        assertEquals(a.storageId, AccountKey("https://a.example", "u1").storageId)
        assertNotEquals(a.storageId, AccountKey("https://a.example", "u2").storageId)
    }
}
