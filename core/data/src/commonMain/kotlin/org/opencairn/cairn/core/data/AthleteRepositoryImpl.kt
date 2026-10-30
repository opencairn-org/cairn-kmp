package org.opencairn.cairn.core.data

import cairn.v1.DeleteAthleteMetricRequest
import cairn.v1.ListAthleteMetricsRequest
import cairn.v1.ListAthleteMetricsResponse
import cairn.v1.UpsertAthleteMetricRequest
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.AthleteMetricEntry
import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.core.network.connect.apiResult

class AthleteRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : AthleteRepository {
    override fun observeProfile(): Flow<Cached<AthleteProfile>> =
        snapshots.observe(KEY) { ListAthleteMetricsResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshProfile(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .metrics
                    .ListAthleteMetrics()
                    .execute(ListAthleteMetricsRequest())
            snapshots.put(KEY, response.encode())
        }

    override suspend fun addEntry(
        key: String,
        date: String,
        value: Double,
    ): Result<Unit> =
        apiResult {
            scope
                .api()
                .metrics
                .UpsertAthleteMetric()
                .execute(UpsertAthleteMetricRequest(key = key, effective_date = date, value_ = value))
        }.mapCatching { refreshProfile().getOrThrow() }

    override suspend fun deleteEntry(id: String): Result<Unit> =
        apiResult {
            scope
                .api()
                .metrics
                .DeleteAthleteMetric()
                .execute(DeleteAthleteMetricRequest(id = id))
        }.mapCatching { refreshProfile().getOrThrow() }

    private companion object {
        const val KEY = "athlete-metrics"
    }
}

internal fun ListAthleteMetricsResponse.toDomain() =
    AthleteProfile(
        specs = specs.map { AthleteMetricSpec(it.key, it.label, it.unit, it.min, it.max) },
        entries = entries.map { AthleteMetricEntry(it.id, it.key, it.effective_date, it.value_) },
    )
