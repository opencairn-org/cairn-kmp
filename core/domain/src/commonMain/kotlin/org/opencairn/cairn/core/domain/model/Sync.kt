package org.opencairn.cairn.core.domain.model

import kotlin.time.Instant

enum class SyncAccountStatus { ACTIVE, AUTH_INVALID, WORKER_OFFLINE, RATE_LIMITED, NEEDS_MIGRATION, DISABLED, UNKNOWN }

/** Import queue of one connected account, by state. */
data class QueueCounts(
    val pending: Int = 0,
    val inProgress: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
) {
    /** Items still to be imported. */
    val open: Int get() = pending + inProgress
}

/** How much of a provider's API budget is used in one window. */
data class RateLimitWindow(
    val used: Int,
    val limit: Int,
    val resetsAt: Instant?,
)

/** A connected provider account (Strava, Garmin, …) with its sync state. */
data class SyncAccount(
    val id: String,
    val provider: String,
    val label: String,
    val status: SyncAccountStatus,
    val statusReason: String,
    val lastSyncAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
    /** Activities imported so far and the provider's own count (0 = never discovered). */
    val imported: Int,
    val providerTotal: Int,
    /** Start of the newest activity the server has seen from this account. */
    val newestActivityAt: Instant?,
    val discovering: Boolean,
    val rateLimited: Boolean,
    val autoImport: Boolean,
    val queue: QueueCounts,
    val shortWindow: RateLimitWindow?,
    val dailyWindow: RateLimitWindow?,
) {
    val displayName: String get() = label.ifBlank { provider.replaceFirstChar { it.uppercase() } }

    /** Whether the account currently brings activities in without the user doing anything. */
    val healthy: Boolean get() = status == SyncAccountStatus.ACTIVE && autoImport && !rateLimited
}

/** One line of an account's import history. */
data class SyncEvent(
    /** `sync_started`, `activity_imported`, `activity_updated` or `failed`. */
    val kind: String,
    val count: Int,
    val detail: String,
    val occurredAt: Instant?,
    /** The imported activity, when the event names one that is in. */
    val activityId: String?,
    /** `failed`, `completed` or `ongoing`. */
    val status: String,
)

/** An item waiting in (or stuck in) an account's import queue. */
data class QueueItem(
    val id: String,
    val type: String,
    val externalId: String,
    val status: String,
    val attempts: Int,
    val lastError: String,
    val createdAt: Instant?,
    /** When the queued activity took place, if the provider told. */
    val itemTime: Instant?,
)
