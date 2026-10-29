package org.opencairn.cairn.core.data

import cairn.v1.TimeRange
import org.opencairn.cairn.core.domain.model.ActivityFacets
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.model.Facet
import cairn.v1.ActivityFacets as ProtoFacets
import cairn.v1.ActivityFilter as ProtoFilter
import cairn.v1.ActivitySort as ProtoSort

fun ActivityFilter.toProto(): ProtoFilter =
    ProtoFilter(
        types = types.map { it.toProto() },
        disciplines = disciplines.toList(),
        time_range = if (from != null || to != null) TimeRange(from = from?.toWire(), to = to?.toWire()) else null,
        gear_ids = gearIds.toList(),
        virtual = virtual.toProto(),
        ebike = ebike.toProto(),
        commute = commute.toProto(),
        race = race.toProto(),
        distance_min_m = distanceMinM,
        distance_max_m = distanceMaxM,
        duration_min_s = durationMinS,
        duration_max_s = durationMaxS,
        elevation_min_m = elevationMinM,
        elevation_max_m = elevationMaxM,
        speed_min_mps = speedMinMps,
        speed_max_mps = speedMaxMps,
        hr_min_bpm = hrMinBpm,
        hr_max_bpm = hrMaxBpm,
        power_min_w = powerMinW,
        power_max_w = powerMaxW,
    )

private fun ActivityFilter.Flag.toProto() =
    when (this) {
        ActivityFilter.Flag.ANY -> ProtoFilter.Flag.FLAG_UNSPECIFIED
        ActivityFilter.Flag.ONLY -> ProtoFilter.Flag.FLAG_ONLY
        ActivityFilter.Flag.EXCLUDE -> ProtoFilter.Flag.FLAG_EXCLUDE
    }

fun ActivitySort.toProto(): ProtoSort =
    when (this) {
        ActivitySort.DATE_DESC -> ProtoSort.ACTIVITY_SORT_START_TIME_DESC
        ActivitySort.DATE_ASC -> ProtoSort.ACTIVITY_SORT_START_TIME_ASC
        ActivitySort.DISTANCE_DESC -> ProtoSort.ACTIVITY_SORT_DISTANCE_DESC
        ActivitySort.DURATION_DESC -> ProtoSort.ACTIVITY_SORT_DURATION_DESC
        ActivitySort.ELEVATION_DESC -> ProtoSort.ACTIVITY_SORT_ELEVATION_DESC
        ActivitySort.SPEED_DESC -> ProtoSort.ACTIVITY_SORT_SPEED_DESC
    }

fun ProtoFacets.toDomain() =
    ActivityFacets(
        types = types.map { Facet(it.value_, it.count) },
        disciplines = disciplines.map { Facet(it.value_, it.count) },
        years = years.map { Facet(it.value_, it.count) },
    )
