package org.opencairn.cairn.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.AppRelease
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.model.UnreadCount

/** The connected provider accounts and what their imports are doing. */
interface SyncRepository {
    fun observeAccounts(): Flow<Cached<List<SyncAccount>>>

    suspend fun refreshAccounts(): Result<Unit>

    /** Import history of one account, newest first. */
    fun observeHistory(accountId: String): Flow<Cached<List<SyncEvent>>>

    suspend fun refreshHistory(accountId: String): Result<Unit>

    /** Queue items that are pending, running or failed. */
    fun observeQueue(accountId: String): Flow<Cached<List<QueueItem>>>

    suspend fun refreshQueue(accountId: String): Result<Unit>
}

interface NotificationRepository {
    /** Newest first. */
    fun observeNotifications(): Flow<Cached<List<Notification>>>

    suspend fun refreshNotifications(): Result<Unit>

    fun observeUnread(): Flow<Cached<UnreadCount>>

    suspend fun refreshUnread(): Result<Unit>

    suspend fun markRead(id: String): Result<Unit>

    suspend fun markAllRead(): Result<Unit>
}

/**
 * What the server pushes while the app is open. Both flows hold one connection each for as long
 * as they are collected, reconnect on their own and stay silent on servers without streaming.
 */
interface LiveEventsRepository {
    val activityChanges: Flow<ActivityChange>

    /** Emits once per notification that arrives. */
    val notificationArrivals: Flow<Unit>
}

/** The update notice for builds that do not update through a store. */
interface UpdateRepository {
    /** A release newer than the running app that the user has not dismissed; null when there is none. */
    val availableUpdate: Flow<AppRelease?>

    /** Whether the app looks for new releases at all (the user's setting; on by default). */
    val checkEnabled: Flow<Boolean>

    /** False on builds that update through a store or a packager; the setting is hidden there. */
    val supported: Boolean

    suspend fun setCheckEnabled(enabled: Boolean)

    /** Asks the release page at most once a day; does nothing when unsupported or switched off. */
    suspend fun checkIfDue(): Result<Unit>

    /** Hides the notice for [version]; a later release shows it again. */
    suspend fun dismiss(version: String)
}
