package org.opencairn.cairn.core.database

import app.cash.sqldelight.db.SqlDriver

/** Opens (and migrates) the SQLite file `name` in the platform's app-data location. */
expect class DatabaseDriverFactory {
    fun create(name: String): SqlDriver
}

fun DatabaseDriverFactory.appDatabase(): CairnDb = CairnDb(create("cairn.db"))
