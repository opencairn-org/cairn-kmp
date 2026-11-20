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
    /** The provider, with the user's own label when they gave the account one: `Garmin (Watch)`. */
    val displayName: String
        get() {
            val name = provider.replaceFirstChar { it.uppercase() }
            return if (label.isBlank()) name else "$name ($label)"
        }

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

/** The one thing about syncing worth telling on the overview, if there is any. */
data class SyncNotice(
    val kind: Kind,
    val account: String,
    /** Activities still to come in; only meaningful for [Kind.IMPORTING]. */
    val pending: Int = 0,
) {
    enum class Kind { NEEDS_SIGN_IN, IMPORTING, DISCOVERING, RATE_LIMITED, WORKER_OFFLINE }
}

/**
 * Why a recent activity may not be here yet, most actionable reason first: an account that lost
 * its sign-in, then work in progress, then accounts that are waiting on the provider or a worker.
 * Null when every account is idle and healthy; disabled accounts are the user's own choice.
 */
fun List<SyncAccount>.syncNotice(): SyncNotice? {
    fun first(
        kind: SyncNotice.Kind,
        match: (SyncAccount) -> Boolean,
    ) = firstOrNull(match)?.let { SyncNotice(kind, it.displayName, it.queue.open) }
    return first(SyncNotice.Kind.NEEDS_SIGN_IN) { it.status == SyncAccountStatus.AUTH_INVALID }
        ?: first(SyncNotice.Kind.IMPORTING) { it.queue.open > 0 }
        ?: first(SyncNotice.Kind.DISCOVERING) { it.discovering }
        ?: first(SyncNotice.Kind.RATE_LIMITED) { it.rateLimited || it.status == SyncAccountStatus.RATE_LIMITED }
        ?: first(SyncNotice.Kind.WORKER_OFFLINE) { it.status == SyncAccountStatus.WORKER_OFFLINE }
}
