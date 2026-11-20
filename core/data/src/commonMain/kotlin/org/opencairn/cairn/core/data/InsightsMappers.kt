package org.opencairn.cairn.core.data

import cairn.v1.ActivityLap
import cairn.v1.ActivitySegmentEffort
import cairn.v1.GetActivityZonesResponse
import cairn.v1.ListKudosResponse
import cairn.v1.ListSimilarActivitiesResponse
import org.opencairn.cairn.core.domain.model.ActivityZones
import org.opencairn.cairn.core.domain.model.Comment
import org.opencairn.cairn.core.domain.model.Decoupling
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.Kudos
import org.opencairn.cairn.core.domain.model.Lap
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.SegmentEffort
import org.opencairn.cairn.core.domain.model.SimilarActivities
import org.opencairn.cairn.core.domain.model.SimilarActivity
import org.opencairn.cairn.core.domain.model.ZoneBand
import org.opencairn.cairn.core.domain.model.ZoneSummary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import cairn.v1.Comment as ProtoComment
import cairn.v1.ExportFormat as ProtoExportFormat
import cairn.v1.ZoneSummary as ProtoZoneSummary

internal fun ActivityLap.toDomain() =
    Lap(
        index = index,
        label = label,
        startOffset = start_offset_s.seconds,
        elapsed = elapsed_duration?.toDuration() ?: Duration.ZERO,
        moving = moving_duration?.toDuration() ?: Duration.ZERO,
        distance = distance_m?.let(::Meters),
        avgSpeed = avg_speed_mps?.let(::MetersPerSecond),
        avgHeartRate = avg_heart_rate_bpm,
        avgPower = avg_power_w,
        elevationGain = elevation_gain_m?.let(::Meters),
    )

private fun ProtoZoneSummary.toDomain() =
    ZoneSummary(
        basis = basis,
        reference = reference,
        totalSeconds = total_seconds,
        bands = zones.map { ZoneBand(it.label, it.low_pct, it.high_pct, it.seconds) },
    )

internal fun GetActivityZonesResponse.toDomain() =
    ActivityZones(
        heartRate = hr?.toDomain(),
        power = power?.toDomain(),
        decoupling = decoupling?.let { Decoupling(it.pct, it.basis, it.coupled) },
    )

internal fun ActivitySegmentEffort.toDomain() =
    SegmentEffort(
        segmentId = segment_id,
        segmentName = segment_name,
        segmentDistance = Meters(segment_distance_m),
        climbCategory = climb_category?.ifBlank { null },
        elapsed = elapsed_s.seconds,
        avgHeartRate = avg_heart_rate,
        avgPower = avg_power,
        isPersonalRecord = is_personal_record,
        personalRank = personal_rank,
    )

internal fun ListSimilarActivitiesResponse.toDomain() =
    SimilarActivities(
        activities =
            activities.map {
                SimilarActivity(
                    id = it.id,
                    isCurrent = it.is_current,
                    title = it.title,
                    type = it.type.toDomain(),
                    startTime = it.start_time?.toInstant(),
                    distance = it.distance_m?.let(::Meters),
                    moving = it.moving_s.seconds,
                    avgHeartRate = it.avg_heart_rate,
                    avgPower = it.avg_power,
                    overlap = it.overlap_pct,
                )
            },
        bestMoving = best_moving_s.seconds,
        currentRank = current_rank,
        isBest = is_best,
    )

internal fun ListKudosResponse.toDomain() = Kudos(count, has_kudos, kudosers.map { it.toDomain() })

internal fun ProtoComment.toDomain() = Comment(id, body, created_at?.toInstant(), author?.toDomain())

internal fun ExportFormat.toProto() =
    when (this) {
        ExportFormat.GPX -> ProtoExportFormat.EXPORT_FORMAT_GPX
        ExportFormat.TCX -> ProtoExportFormat.EXPORT_FORMAT_TCX
        ExportFormat.FIT -> ProtoExportFormat.EXPORT_FORMAT_FIT
    }
