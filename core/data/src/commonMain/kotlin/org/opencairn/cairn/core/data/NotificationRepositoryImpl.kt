package org.opencairn.cairn.core.data

import cairn.v1.CountUnreadNotificationsRequest
import cairn.v1.CountUnreadNotificationsResponse
import cairn.v1.ListNotificationsRequest
import cairn.v1.ListNotificationsResponse
import cairn.v1.MarkAllNotificationsReadRequest
import cairn.v1.MarkNotificationReadRequest
import cairn.v1.NotificationEvent
import cairn.v1.PageRequest
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.NotificationSeverity
import org.opencairn.cairn.core.domain.model.UnreadCount
import org.opencairn.cairn.core.domain.repository.NotificationRepository
import org.opencairn.cairn.core.network.connect.apiResult
import cairn.v1.NotificationSeverity as ProtoSeverity

class NotificationRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : NotificationRepository {
    override fun observeNotifications(): Flow<Cached<List<Notification>>> =
        snapshots.observe(LIST) { b ->
            ListNotificationsResponse.ADAPTER
                .decode(b)
                .notifications
                .map { it.toDomain() }
        }

    override suspend fun refreshNotifications(): Result<Unit> =
        apiResult {
            val request = ListNotificationsRequest(page = PageRequest(limit = PAGE_SIZE))
            val response =
                scope
                    .api()
                    .notifications
                    .ListNotifications()
                    .execute(request)
            snapshots.put(LIST, response.encode())
        }

    override fun observeUnread(): Flow<Cached<UnreadCount>> =
        snapshots.observe(UNREAD) { b ->
            CountUnreadNotificationsResponse.ADAPTER
                .decode(
                    b,
                ).let { UnreadCount(it.count, it.count_warn, it.count_error) }
        }

    override suspend fun refreshUnread(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .notifications
                    .CountUnreadNotifications()
                    .execute(CountUnreadNotificationsRequest())
            snapshots.put(UNREAD, response.encode())
        }

    override suspend fun markRead(id: String): Result<Unit> =
        apiResult {
            scope
                .api()
                .notifications
                .MarkNotificationRead()
                .execute(MarkNotificationReadRequest(id = id))
            reload()
        }

    override suspend fun markAllRead(): Result<Unit> =
        apiResult {
            scope
                .api()
                .notifications
                .MarkAllNotificationsRead()
                .execute(MarkAllNotificationsReadRequest())
            reload()
        }

    /** After a change of the read state both the list and the badge are stale. */
    private suspend fun reload() {
        refreshNotifications().getOrThrow()
        refreshUnread().getOrThrow()
    }

    private companion object {
        const val LIST = "notifications"
        const val UNREAD = "notifications-unread"
        const val PAGE_SIZE = 50
    }
}

internal fun NotificationEvent.toDomain() =
    Notification(
        id = id,
        type = type.name.removePrefix("NOTIFICATION_EVENT_TYPE_").lowercase(),
        severity =
            when (severity) {
                ProtoSeverity.NOTIFICATION_SEVERITY_WARN -> NotificationSeverity.WARN
                ProtoSeverity.NOTIFICATION_SEVERITY_ERROR -> NotificationSeverity.ERROR
                else -> NotificationSeverity.INFO
            },
        params = i18n_params,
        activityId = activity_id?.ifBlank { null },
        count = coalesce_count.coerceAtLeast(1),
        read = read,
        createdAt = created_at?.toInstant(),
    )
