package org.opencairn.cairn.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.opencairn.cairn.core.auth.SessionManager
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.network.CairnApi

/** Resolves the active account for cache scoping and the API for refreshes. */
class CacheScope(
    private val session: SessionManager,
) {
    val accountFlow: Flow<AccountKey?> get() = session.activeAccount

    suspend fun account(): String = session.activeAccountKey()?.storageId ?: throw AppError.Unauthenticated()

    suspend fun api(): CairnApi = session.api()

    /** Re-subscribes `source` whenever the active account changes. */
    fun <T> perAccount(source: (String?) -> Flow<T>): Flow<T> = accountFlow.flatMapLatest { source(it?.storageId) }
}
