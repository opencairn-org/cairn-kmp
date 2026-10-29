package org.opencairn.cairn.core.data

import cairn.v1.ContentUrl
import cairn.v1.GetOverviewResponse
import cairn.v1.TimeRange
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Facet
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Meters
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.core.domain.model.UserCard
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import cairn.v1.FeedItem as ProtoFeedItem
import cairn.v1.Totals as ProtoTotals
import cairn.v1.UserCard as ProtoUserCard

fun ProtoTotals?.toDomain() =
    Totals(
        count = this?.count ?: 0,
        distance = Meters(this?.distance_m ?: 0.0),
        moving = (this?.moving_s ?: 0.0).seconds,
        elevationGain = Meters(this?.elevation_gain_m ?: 0.0),
    )

private fun TimeRange?.toPair(): Pair<Instant, Instant>? {
    val from = this?.from?.toInstant() ?: return null
    val to = this.to?.toInstant() ?: return null
    return from to to
}

fun GetOverviewResponse.toDomain() =
    Overview(
        totals = totals.toDomain(),
        bySport = by_sport.map { Facet(it.value_, it.count) },
        recent = recent.map { it.toDomain() },
        thisWeek = this_week.toDomain(),
        lastWeek = last_week.toDomain(),
        thisWeekRange = this_week_range.toPair(),
        lastWeekRange = last_week_range.toPair(),
    )

fun ProtoUserCard.toDomain() =
    UserCard(id, username, display_name.ifBlank { username }, avatar_url.ifBlank { null }, remote)

internal fun ContentUrl?.urlOrNull(): String? = this?.url?.ifBlank { null }

fun ProtoFeedItem.toDomain(): FeedItem? {
    val card = owner?.toDomain()
    return own?.toFeedItem(card) ?: projected?.toFeedItem(card) ?: federated?.toFeedItem(card)
}

private fun cairn.v1.Activity.toFeedItem(owner: UserCard?) =
    FeedItem(
        source = FeedItem.Source.SELF,
        owner = owner,
        id = id,
        title = title,
        type = type.toDomain(),
        sport = type.toDomain().wire,
        startTime = start_time?.toInstant() ?: Instant.fromEpochSeconds(0),
        distance = summary?.distance_m?.let(::Meters),
        elevationGain = summary?.elevation_gain_m?.let(::Meters),
        elapsed = elapsed_duration?.toDuration(),
        startPlace = start_place,
        mapImageUrl = map_image.urlOrNull(),
        externalUrl = null,
    )

private fun cairn.v1.ProjectedActivity.toFeedItem(owner: UserCard?) =
    FeedItem(
        source = FeedItem.Source.FOLLOWING,
        owner = owner,
        id = id,
        title = title,
        type = type.toDomain(),
        sport = type.toDomain().wire,
        startTime = start_time?.toInstant() ?: Instant.fromEpochSeconds(0),
        distance = distance_m?.let(::Meters),
        elevationGain = elevation_gain_m?.let(::Meters),
        elapsed = elapsed_duration_s.seconds,
        startPlace = start_place,
        mapImageUrl = map_image.urlOrNull(),
        externalUrl = null,
    )

private fun cairn.v1.FederatedActivity.toFeedItem(owner: UserCard?) =
    FeedItem(
        source = FeedItem.Source.FEDERATED,
        owner = owner,
        id = id,
        title = title,
        type = ActivityType.fromWire(sport),
        sport = sport,
        startTime = start_time?.toInstant() ?: Instant.fromEpochSeconds(0),
        distance = distance_m?.let(::Meters),
        elevationGain = elevation_gain_m?.let(::Meters),
        elapsed = elapsed_duration_s?.seconds,
        startPlace = "",
        mapImageUrl = map_image.urlOrNull(),
        externalUrl = url.ifBlank { null },
    )
