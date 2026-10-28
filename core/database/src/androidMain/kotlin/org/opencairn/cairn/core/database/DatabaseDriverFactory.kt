package org.opencairn.cairn.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(
    private val context: Context,
) {
    actual fun create(name: String): SqlDriver = AndroidSqliteDriver(CairnDb.Schema, context, name)
}
