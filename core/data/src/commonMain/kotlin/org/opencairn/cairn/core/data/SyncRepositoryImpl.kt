package org.opencairn.cairn.core.data

import cairn.v1.ExternalAccount
import cairn.v1.ExternalAccountStatus
import cairn.v1.ListExternalAccountsRequest
import cairn.v1.ListExternalAccountsResponse
import cairn.v1.ListQueueItemsRequest
import cairn.v1.ListQueueItemsResponse
import cairn.v1.ListSyncHistoryRequest
import cairn.v1.ListSyncHistoryResponse
import cairn.v1.PageRequest
import kotlinx.coroutines.flow.Flow
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.QueueCounts
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.RateLimitWindow
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.repository.SyncRepository
import org.opencairn.cairn.core.network.connect.apiResult
import cairn.v1.QueueItem as ProtoQueueItem
import cairn.v1.RateLimitWindow as ProtoWindow
import cairn.v1.SyncEvent as ProtoSyncEvent

class SyncRepositoryImpl(
    private val scope: CacheScope,
    private val snapshots: SnapshotStore,
) : SyncRepository {
    override fun observeAccounts(): Flow<Cached<List<SyncAccount>>> =
        snapshots.observe(
            ACCOUNTS,
        ) { b ->
            ListExternalAccountsResponse.ADAPTER
                .decode(b)
                .accounts
                .map { it.toDomain() }
        }

    override suspend fun refreshAccounts(): Result<Unit> =
        apiResult {
            val response =
                scope
                    .api()
                    .externalAccounts
                    .ListExternalAccounts()
                    .execute(ListExternalAccountsRequest())
            snapshots.put(ACCOUNTS, response.encode())
        }

    override fun observeHistory(accountId: String): Flow<Cached<List<SyncEvent>>> =
        snapshots.observe("sync-history:$accountId") { b ->
            ListSyncHistoryResponse.ADAPTER
                .decode(b)
                .events
                .map { it.toDomain() }
        }

    override suspend fun refreshHistory(accountId: String): Result<Unit> =
        apiResult {
            val request = ListSyncHistoryRequest(account_id = accountId, page = PageRequest(limit = HISTORY_SIZE))
            val response =
                scope
                    .api()
                    .externalAccounts
                    .ListSyncHistory()
                    .execute(request)
            snapshots.put("sync-history:$accountId", response.encode())
        }

    override fun observeQueue(accountId: String): Flow<Cached<List<QueueItem>>> =
        snapshots.observe("sync-queue:$accountId") { b ->
            ListQueueItemsResponse.ADAPTER
                .decode(b)
                .items
                .map { it.toDomain() }
        }

    override suspend fun refreshQueue(accountId: String): Result<Unit> =
        apiResult {
            val request = ListQueueItemsRequest(account_id = accountId, page = PageRequest(limit = QUEUE_SIZE))
            val response =
                scope
                    .api()
                    .externalAccounts
                    .ListQueueItems()
                    .execute(request)
            snapshots.put("sync-queue:$accountId", response.encode())
        }

    private companion object {
        const val ACCOUNTS = "sync-accounts"
        const val HISTORY_SIZE = 50
        const val QUEUE_SIZE = 100
    }
}

internal fun ExternalAccount.toDomain() =
    SyncAccount(
        id = id,
        provider = provider,
        label = display_label,
        status = status.toDomain(),
        statusReason = status_reason,
        lastSyncAt = last_sync_at?.toInstant(),
        lastSuccessfulSyncAt = last_successful_sync_at?.toInstant(),
        imported = imported,
        providerTotal = provider_total,
        newestActivityAt = watermark?.toInstant(),
        discovering = discovering,
        rateLimited = rate_limited,
        autoImport = auto_import_enabled,
        queue =
            queue?.let { QueueCounts(it.pending, it.in_progress, it.done, it.failed, it.skipped) } ?: QueueCounts(),
        shortWindow = rate_limit_budget?.short?.toDomain(),
        dailyWindow = rate_limit_budget?.daily?.toDomain(),
    )

private fun ProtoWindow.toDomain() = RateLimitWindow(used, limit, resets_at?.toInstant()).takeIf { limit > 0 }

internal fun ExternalAccountStatus.toDomain() =
    when (this) {
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_ACTIVE -> SyncAccountStatus.ACTIVE
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_AUTH_INVALID -> SyncAccountStatus.AUTH_INVALID
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_WORKER_OFFLINE -> SyncAccountStatus.WORKER_OFFLINE
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_RATE_LIMITED -> SyncAccountStatus.RATE_LIMITED
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_NEEDS_MIGRATION -> SyncAccountStatus.NEEDS_MIGRATION
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_DISABLED -> SyncAccountStatus.DISABLED
        ExternalAccountStatus.EXTERNAL_ACCOUNT_STATUS_UNSPECIFIED -> SyncAccountStatus.UNKNOWN
    }

internal fun ProtoSyncEvent.toDomain() =
    SyncEvent(
        kind = kind,
        count = count,
        detail = detail,
        occurredAt = occurred_at?.toInstant(),
        activityId = activity_id.ifBlank { null },
        status = status,
    )

internal fun ProtoQueueItem.toDomain() =
    QueueItem(
        id = id,
        type = item_type,
        externalId = external_id,
        status = status,
        attempts = attempts,
        lastError = last_error,
        createdAt = created_at?.toInstant(),
        itemTime = item_time?.toInstant(),
    )
