package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.Cached

/** The athlete's dated physiological parameters; cached per account, edits go to the server. */
interface AthleteRepository {
    fun observeProfile(): Flow<Cached<AthleteProfile>>

    suspend fun refreshProfile(): Result<Unit>

    /** Adds or replaces the value of [key] effective from [date] (ISO `YYYY-MM-DD`). */
    suspend fun addEntry(
        key: String,
        date: String,
        value: Double,
    ): Result<Unit>

    suspend fun deleteEntry(id: String): Result<Unit>
}
