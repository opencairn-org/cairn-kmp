package org.opencairn.cairn.core.domain.model

import kotlin.time.Duration
import kotlin.time.Instant

data class UserCard(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val remote: Boolean,
)

/** One card on the home feed; own activities keep their full model, others are projections. */
data class FeedItem(
    val source: Source,
    val owner: UserCard?,
    val id: String,
    val title: String,
    val type: ActivityType,
    val sport: String,
    val startTime: Instant,
    val distance: Meters?,
    val elevationGain: Meters?,
    val elapsed: Duration?,
    val startPlace: String,
    val mapImageUrl: String?,
    val externalUrl: String?,
) {
    enum class Source { SELF, FOLLOWING, FEDERATED }
}
