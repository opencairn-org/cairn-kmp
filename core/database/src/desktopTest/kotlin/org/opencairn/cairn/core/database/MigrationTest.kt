package org.opencairn.cairn.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MigrationTest {
    /** A database file as release 0.1.x left it: the committed version 1 schema with `user_version` 1. */
    private fun versionOneFile(): File {
        val dir = Files.createTempDirectory("cairn-migration").toFile()
        val file = File(dir, "cairn.db")
        File("src/commonMain/sqldelight/schema/1.db").copyTo(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            driver.execute(null, "INSERT INTO snapshot(account, key, bytes, fetched_at) VALUES ('a', 'k', x'01', 1)", 0)
            driver.execute(null, "PRAGMA user_version = 1", 0)
        }
        return file
    }

    @Test
    fun versionOneDatabaseGainsAppSettingsAndKeepsItsData() {
        val file = versionOneFile()
        val db = CairnDb(DatabaseDriverFactory(file.parentFile).create(file.name))
        assertEquals(
            1,
            db.cacheQueries
                .snapshotByKey("a", "k")
                .executeAsOne()
                .bytes.size,
        )
        assertNull(db.settingsQueries.byKey("update.enabled").executeAsOneOrNull())
        db.settingsQueries.put("update.enabled", "false")
        assertEquals("false", db.settingsQueries.byKey("update.enabled").executeAsOne())
    }

    @Test
    fun freshDatabaseIsCreatedAtTheCurrentVersion() {
        val dir = Files.createTempDirectory("cairn-fresh").toFile()
        val db = CairnDb(DatabaseDriverFactory(dir).create("cairn.db"))
        db.settingsQueries.put("k", "v")
        db.settingsQueries.remove("k")
        assertNull(db.settingsQueries.byKey("k").executeAsOneOrNull())
        assertEquals(2L, CairnDb.Schema.version)
    }
}
