package org.opencairn.cairn.core.domain.model

import kotlin.time.Instant

/** An all-time best for one (sport, metric, window) and the activity that holds it. */
data class PersonalRecord(
    val activityType: ActivityType,
    val metric: BestEffortMetric,
    val windowKind: BestEffortWindowKind,
    val windowValue: Int,
    val achievedValue: Double,
    val activityId: String,
    val achievedAt: Instant?,
)

/** Which best-effort bucket a history is asked for. */
data class RecordKey(
    val activityType: ActivityType,
    val metric: BestEffortMetric,
    val windowKind: BestEffortWindowKind,
    val windowValue: Int,
)

data class BestEffortHistoryItem(
    val activityId: String,
    val title: String,
    val startTime: Instant?,
    val achievedValue: Double,
    val isBest: Boolean,
)

/** The progression of one bucket across activities, oldest first. */
data class BestEffortHistory(
    val best: Double,
    val count: Int,
    val items: List<BestEffortHistoryItem>,
)
