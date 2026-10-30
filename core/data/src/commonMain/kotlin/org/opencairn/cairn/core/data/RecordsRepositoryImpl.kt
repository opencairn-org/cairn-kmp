package org.opencairn.cairn.core.data

import cairn.v1.GetBestEffortHistoryRequest
import cairn.v1.GetBestEffortHistoryResponse
import cairn.v1.ListRecordsRequest
import cairn.v1.ListRecordsResponse
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.BestEffortHistory
import org.opencairn.cairn.core.domain.model.BestEffortHistoryItem
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import org.opencairn.cairn.core.network.connect.apiResult
import cairn.v1.BestEffortMetric as ProtoMetric
import cairn.v1.BestEffortWindowKind as ProtoWindow
import cairn.v1.UserRecord as ProtoRecord

class RecordsRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : RecordsRepository {
    override fun observeRecords(): Flow<Cached<List<PersonalRecord>>> =
        snapshots.observe(RECORDS) { bytes ->
            ListRecordsResponse.ADAPTER
                .decode(bytes)
                .records
                .map { it.toDomain() }
        }

    override suspend fun refreshRecords(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .activities
                    .ListRecords()
                    .execute(ListRecordsRequest())
            snapshots.put(RECORDS, response.encode())
        }

    override fun observeHistory(key: RecordKey): Flow<Cached<BestEffortHistory>> =
        snapshots.observe(historyKey(key)) { GetBestEffortHistoryResponse.ADAPTER.decode(it).toDomain() }

    override suspend fun refreshHistory(key: RecordKey): Result<Unit> =
        apiResult {
            val request =
                GetBestEffortHistoryRequest(
                    type = key.activityType.toProto(),
                    metric = key.metric.toProto(),
                    window_kind = key.windowKind.toProto(),
                    window_value = key.windowValue,
                )
            val response =
                scope
                    .api()
                    .stats
                    .GetBestEffortHistory()
                    .execute(request)
            snapshots.put(historyKey(key), response.encode())
        }

    private fun historyKey(key: RecordKey) =
        "record-history:${key.activityType.wire}:${key.metric.name}:${key.windowKind.name}:${key.windowValue}"

    private companion object {
        const val RECORDS = "records"
    }
}

internal fun ProtoRecord.toDomain() =
    PersonalRecord(
        activityType = activity_type.toDomain(),
        metric = metric.toDomain(),
        windowKind = window_kind.toDomain(),
        windowValue = window_value,
        achievedValue = achieved_value,
        activityId = activity_id,
        achievedAt = ts?.toInstant(),
    )

internal fun GetBestEffortHistoryResponse.toDomain() =
    BestEffortHistory(
        best = best,
        count = count,
        items =
            items.map {
                BestEffortHistoryItem(
                    it.activity_id,
                    it.title,
                    it.start_time?.toInstant(),
                    it.achieved_value,
                    it.is_best,
                )
            },
    )

internal fun ProtoMetric.toDomain() =
    when (this) {
        ProtoMetric.BEST_EFFORT_METRIC_SPEED -> BestEffortMetric.SPEED
        ProtoMetric.BEST_EFFORT_METRIC_POWER -> BestEffortMetric.POWER
        ProtoMetric.BEST_EFFORT_METRIC_HEART_RATE -> BestEffortMetric.HEART_RATE
        ProtoMetric.BEST_EFFORT_METRIC_VAM -> BestEffortMetric.VAM
        else -> BestEffortMetric.PACE
    }

internal fun BestEffortMetric.toProto() =
    ProtoMetric.entries.first { it != ProtoMetric.BEST_EFFORT_METRIC_UNSPECIFIED && it.toDomain() == this }

internal fun ProtoWindow.toDomain() =
    if (this == ProtoWindow.BEST_EFFORT_WINDOW_KIND_DURATION) {
        BestEffortWindowKind.DURATION
    } else {
        BestEffortWindowKind.DISTANCE
    }

internal fun BestEffortWindowKind.toProto() =
    when (this) {
        BestEffortWindowKind.DISTANCE -> ProtoWindow.BEST_EFFORT_WINDOW_KIND_DISTANCE
        BestEffortWindowKind.DURATION -> ProtoWindow.BEST_EFFORT_WINDOW_KIND_DURATION
    }
