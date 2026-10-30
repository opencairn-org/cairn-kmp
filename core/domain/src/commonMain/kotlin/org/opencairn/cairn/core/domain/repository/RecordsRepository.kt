package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.BestEffortHistory
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.RecordKey

/** Personal records and the history behind each one. */
interface RecordsRepository {
    fun observeRecords(): Flow<Cached<List<PersonalRecord>>>

    suspend fun refreshRecords(): Result<Unit>

    fun observeHistory(key: RecordKey): Flow<Cached<BestEffortHistory>>

    suspend fun refreshHistory(key: RecordKey): Result<Unit>
}
