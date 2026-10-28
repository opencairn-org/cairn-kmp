package org.opencairn.cairn.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun create(name: String): SqlDriver = NativeSqliteDriver(CairnDb.Schema, name)
}
