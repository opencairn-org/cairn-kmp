package org.opencairn.cairn.core.data

import cairn.v1.ActivitySourceStatus
import com.squareup.wire.ofEpochSecond
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivitySourceInfo
import org.opencairn.cairn.core.domain.model.ActivitySummary
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.GearSetBy
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.MetersPerSecond
import org.opencairn.cairn.core.domain.model.Privacy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import cairn.v1.ActivitySummary as ProtoSummary
import cairn.v1.ActivityType as ProtoType
import cairn.v1.GearSetBy as ProtoGearSetBy
import cairn.v1.Privacy as ProtoPrivacy
import com.squareup.wire.Duration as WireDuration
import com.squareup.wire.Instant as WireInstant

fun WireInstant.toInstant(): Instant = Instant.fromEpochSeconds(getEpochSecond(), getNano())

fun WireDuration.toDuration(): Duration = getSeconds().seconds + getNano().nanoseconds

fun Instant.toWire(): WireInstant = ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

fun ProtoType.toDomain(): ActivityType =
    when (this) {
        ProtoType.ACTIVITY_TYPE_RIDE -> ActivityType.RIDE
        ProtoType.ACTIVITY_TYPE_RUN -> ActivityType.RUN
        ProtoType.ACTIVITY_TYPE_SWIM -> ActivityType.SWIM
        ProtoType.ACTIVITY_TYPE_HIKE -> ActivityType.HIKE
        ProtoType.ACTIVITY_TYPE_WALK -> ActivityType.WALK
        ProtoType.ACTIVITY_TYPE_ROW -> ActivityType.ROW
        ProtoType.ACTIVITY_TYPE_SKI -> ActivityType.SKI
        ProtoType.ACTIVITY_TYPE_WORKOUT -> ActivityType.WORKOUT
        ProtoType.ACTIVITY_TYPE_SNOWBOARD -> ActivityType.SNOWBOARD
        ProtoType.ACTIVITY_TYPE_SKATE -> ActivityType.SKATE
        ProtoType.ACTIVITY_TYPE_KAYAK -> ActivityType.KAYAK
        ProtoType.ACTIVITY_TYPE_SUP -> ActivityType.SUP
        ProtoType.ACTIVITY_TYPE_SURF -> ActivityType.SURF
        ProtoType.ACTIVITY_TYPE_GOLF -> ActivityType.GOLF
        ProtoType.ACTIVITY_TYPE_CLIMB -> ActivityType.CLIMB
        ProtoType.ACTIVITY_TYPE_TENNIS -> ActivityType.TENNIS
        ProtoType.ACTIVITY_TYPE_ELLIPTICAL -> ActivityType.ELLIPTICAL
        ProtoType.ACTIVITY_TYPE_WHEELCHAIR -> ActivityType.WHEELCHAIR
        ProtoType.ACTIVITY_TYPE_UNSPECIFIED -> ActivityType.UNSPECIFIED
    }

fun ActivityType.toProto(): ProtoType = ProtoType.entries.first { it.toDomain() == this }

/** `DISCIPLINE_RIDE_GRAVEL` → `ride_gravel`, the web UI's spelling. */
fun cairn.v1.Discipline.toWireName(): String =
    name.removePrefix("DISCIPLINE_").lowercase().takeIf { it != "unspecified" } ?: ""

// One optional per metric; there is no logic here to simplify.
@Suppress("CyclomaticComplexMethod")
fun ProtoSummary.toDomain() =
    ActivitySummary(
        distance = distance_m?.let(::Meters),
        elevationGain = elevation_gain_m?.let(::Meters),
        elevationLoss = elevation_loss_m?.let(::Meters),
        minElevation = min_elevation_m?.let(::Meters),
        maxElevation = max_elevation_m?.let(::Meters),
        avgSpeed = avg_speed_mps?.let(::MetersPerSecond),
        maxSpeed = max_speed_mps?.let(::MetersPerSecond),
        avgHeartRate = avg_heart_rate_bpm,
        maxHeartRate = max_heart_rate_bpm,
        avgPower = avg_power_w,
        maxPower = max_power_w,
        normalizedPower = normalized_power_w,
        avgCadence = avg_cadence,
        maxCadence = max_cadence,
        avgTemperatureC = avg_temperature_c,
        calories = calories_kcal,
        tss = tss,
        intensityFactor = intensity_factor,
        poolLengthM = pool_length_m,
        totalStrokes = total_strokes,
    )

// Field-by-field mapping; the null-coalescing adds up but there is no logic to simplify.
@Suppress("CyclomaticComplexMethod")
fun cairn.v1.Activity.toDomain(): Activity =
    Activity(
        id = id,
        type = type.toDomain(),
        discipline = discipline.toWireName(),
        customSubtype = custom_subtype,
        isVirtual = is_virtual,
        isEbike = is_ebike,
        isCommute = is_commute,
        isRace = is_race,
        title = title,
        description = description,
        startTime = start_time?.toInstant() ?: Instant.fromEpochSeconds(0),
        endTime = end_time?.toInstant(),
        elapsed = elapsed_duration?.toDuration() ?: Duration.ZERO,
        moving = moving_duration?.toDuration() ?: Duration.ZERO,
        timezone = timezone,
        summary = summary?.toDomain() ?: ActivitySummary(),
        tags = tags,
        gearId = gear_id.ifBlank { null },
        gearName = gear_name.ifBlank { null },
        gearSetBy = gear_set_by.toDomain(),
        gearUnset = gear_unset,
        privacy = privacy.toDomain(),
        startPlace = start_place,
        startLat = start_lat,
        startLng = start_lng,
        mapImageUrl = map_image?.url?.ifBlank { null },
        mapImageEtag = map_image_etag,
        primaryStreamSourceId = primary_stream_source_id,
        sources = sources.map { it.toDomain() },
        updatedAt = updated_at?.toInstant(),
    )

fun ProtoGearSetBy.toDomain() =
    when (this) {
        ProtoGearSetBy.GEAR_SET_BY_MERGE -> GearSetBy.MERGE
        ProtoGearSetBy.GEAR_SET_BY_USER -> GearSetBy.USER
        else -> GearSetBy.UNSPECIFIED
    }

fun ProtoPrivacy.toDomain() =
    when (this) {
        ProtoPrivacy.PRIVACY_FOLLOWERS -> Privacy.FOLLOWERS
        ProtoPrivacy.PRIVACY_PUBLIC -> Privacy.PUBLIC
        else -> Privacy.PRIVATE
    }

fun cairn.v1.ActivitySource.toDomain() =
    ActivitySourceInfo(
        id = id,
        provider = provider,
        externalId = external_id,
        status = status.name.removePrefix("ACTIVITY_SOURCE_STATUS_").lowercase(),
        importedAt = imported_at?.toInstant(),
        hasStream = status == ActivitySourceStatus.ACTIVITY_SOURCE_STATUS_ACTIVE,
    )
