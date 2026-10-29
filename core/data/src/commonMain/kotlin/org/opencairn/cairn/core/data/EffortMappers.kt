package org.opencairn.cairn.core.data

import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import kotlin.time.Duration
import cairn.v1.Attachment as ProtoAttachment
import cairn.v1.BestEffort as ProtoBestEffort
import cairn.v1.BestEffortMetric as ProtoMetric
import cairn.v1.BestEffortWindowKind as ProtoWindow

fun ProtoBestEffort.toDomain() =
    BestEffort(
        id = id,
        activityId = activity_id,
        metric =
            when (metric) {
                ProtoMetric.BEST_EFFORT_METRIC_SPEED -> BestEffortMetric.SPEED
                ProtoMetric.BEST_EFFORT_METRIC_POWER -> BestEffortMetric.POWER
                ProtoMetric.BEST_EFFORT_METRIC_HEART_RATE -> BestEffortMetric.HEART_RATE
                ProtoMetric.BEST_EFFORT_METRIC_VAM -> BestEffortMetric.VAM
                else -> BestEffortMetric.PACE
            },
        windowKind =
            if (window_kind == ProtoWindow.BEST_EFFORT_WINDOW_KIND_DURATION) {
                BestEffortWindowKind.DURATION
            } else {
                BestEffortWindowKind.DISTANCE
            },
        windowValue = window_value,
        achievedValue = achieved_value,
        duration = duration?.toDuration() ?: Duration.ZERO,
        startOffset = start_offset,
    )

fun ProtoAttachment.toDomain() =
    Attachment(
        id = id,
        originalUrl = original?.url ?: "",
        mediumUrl = medium.urlOrNull(),
        thumbUrl = thumb.urlOrNull(),
        width = width,
        height = height,
        blurhash = blurhash.ifBlank { null },
        caption = caption,
    )
