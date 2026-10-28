package org.opencairn.cairn.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountsTest {
    private fun db(): CairnDb = CairnDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), CairnDb.Schema))

    @Test
    fun activeAccountIsExclusiveAndUpsertKeepsActiveFlag() {
        val db = db()
        db.upsertAccount(
            "https://a",
            "u1",
            "oauth",
            "jo",
            "jo@a",
            "Jo",
            null,
            "USER",
            "METRIC",
            "LOCALE",
            "LOCALE",
            "de",
            "Europe/Berlin",
            "",
            null,
            1L,
            active = true,
        )
        db.upsertAccount(
            "https://a",
            "u2",
            "pat",
            "ann",
            "ann@a",
            "Ann",
            null,
            "ADMIN",
            "IMPERIAL",
            "ISO",
            "H24",
            "en",
            "UTC",
            "admin",
            null,
            2L,
            active = false,
        )
        assertEquals(
            "u1",
            db.accountsQueries
                .active()
                .executeAsOne()
                .user_id,
        )
        db.upsertAccount(
            "https://a",
            "u1",
            "oauth",
            "jo",
            "jo@a",
            "Johanna",
            null,
            "USER",
            "METRIC",
            "LOCALE",
            "LOCALE",
            "de",
            "Europe/Berlin",
            "",
            null,
            3L,
            active = false,
        )
        val u1 = db.accountsQueries.active().executeAsOne()
        assertEquals("Johanna", u1.display_name)
        db.upsertAccount(
            "https://a",
            "u2",
            "pat",
            "ann",
            "ann@a",
            "Ann",
            null,
            "ADMIN",
            "IMPERIAL",
            "ISO",
            "H24",
            "en",
            "UTC",
            "admin",
            null,
            4L,
            active = true,
        )
        assertEquals(
            "u2",
            db.accountsQueries
                .active()
                .executeAsOne()
                .user_id,
        )
        assertEquals(
            2,
            db.accountsQueries
                .all()
                .executeAsList()
                .size,
        )
    }

    @Test
    fun serverKeepsClientIdOnUpsert() {
        val db = db()
        db.upsertServer("https://a", "v0.5.1", federation = false, oauth = true, clientId = "cid", now = 1L)
        db.upsertServer("https://a", "v0.6.0", federation = true, oauth = true, clientId = null, now = 2L)
        val s = db.serversQueries.byUrl("https://a").executeAsOne()
        assertEquals("cid", s.oauth_client_id)
        assertEquals("v0.6.0", s.version)
        assertEquals(1L, s.federation_enabled)
    }
}
