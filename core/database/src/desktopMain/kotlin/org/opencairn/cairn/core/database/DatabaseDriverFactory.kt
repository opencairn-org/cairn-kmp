package org.opencairn.cairn.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** `dataDir` is the per-user app-data directory (XDG / AppData / Application Support). */
actual class DatabaseDriverFactory(
    private val dataDir: File,
) {
    actual fun create(name: String): SqlDriver {
        dataDir.mkdirs()
        val file = File(dataDir, name)
        return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties(), CairnDb.Schema)
    }
}
