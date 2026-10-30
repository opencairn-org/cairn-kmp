package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearDraft

/** Equipment including retired items; cached per account, edits go to the server then refresh. */
interface GearRepository {
    fun observeGear(): Flow<Cached<List<Gear>>>

    suspend fun refreshGear(): Result<Unit>

    suspend fun createGear(draft: GearDraft): Result<Gear>

    suspend fun updateGear(
        id: String,
        draft: GearDraft,
    ): Result<Gear>

    suspend fun setRetired(
        id: String,
        retired: Boolean,
    ): Result<Unit>

    suspend fun deleteGear(id: String): Result<Unit>
}
