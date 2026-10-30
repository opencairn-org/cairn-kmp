package org.opencairn.cairn.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.domain.model.Cached
import kotlin.time.Clock

/** Whole server responses kept per account under a key; the repositories decode them into domain types. */
class SnapshotStore(
    private val db: CairnDb,
    private val scope: CacheScope,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock = Clock.System,
) {
    fun <T> observe(
        key: String,
        decode: (ByteArray) -> T,
    ): Flow<Cached<T>> =
        scope.perAccount { account ->
            if (account == null) return@perAccount flowOf(Cached(null, null))
            db.cacheQueries
                .snapshotByKey(account, key)
                .asFlow()
                .mapToOneOrNull(dispatchers.io)
                .map { it.toCached(decode) }
        }

    suspend fun put(
        key: String,
        bytes: ByteArray,
    ) {
        val account = scope.account()
        withContext(dispatchers.io) { db.cacheQueries.upsertSnapshot(account, key, bytes, clock.now().epochSeconds) }
    }
}
