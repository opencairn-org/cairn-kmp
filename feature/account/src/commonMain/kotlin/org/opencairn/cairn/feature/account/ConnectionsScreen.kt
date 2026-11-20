package org.opencairn.cairn.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.RateLimitWindow
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncAccountStatus
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_connections
import org.opencairn.cairn.ui.design.resources.connection_auto_import_off
import org.opencairn.cairn.ui.design.resources.connection_budget_daily
import org.opencairn.cairn.ui.design.resources.connection_budget_short
import org.opencairn.cairn.ui.design.resources.connection_discovering
import org.opencairn.cairn.ui.design.resources.connection_history_empty
import org.opencairn.cairn.ui.design.resources.connection_history_title
import org.opencairn.cairn.ui.design.resources.connection_imported
import org.opencairn.cairn.ui.design.resources.connection_imported_count
import org.opencairn.cairn.ui.design.resources.connection_last_sync
import org.opencairn.cairn.ui.design.resources.connection_never_synced
import org.opencairn.cairn.ui.design.resources.connection_newest
import org.opencairn.cairn.ui.design.resources.connection_queue
import org.opencairn.cairn.ui.design.resources.connection_queue_attempts
import org.opencairn.cairn.ui.design.resources.connection_queue_empty
import org.opencairn.cairn.ui.design.resources.connection_queue_title
import org.opencairn.cairn.ui.design.resources.connection_reauthorize_hint
import org.opencairn.cairn.ui.design.resources.connection_status_active
import org.opencairn.cairn.ui.design.resources.connection_status_auth_invalid
import org.opencairn.cairn.ui.design.resources.connection_status_disabled
import org.opencairn.cairn.ui.design.resources.connection_status_needs_migration
import org.opencairn.cairn.ui.design.resources.connection_status_rate_limited
import org.opencairn.cairn.ui.design.resources.connection_status_unknown
import org.opencairn.cairn.ui.design.resources.connection_status_worker_offline
import org.opencairn.cairn.ui.design.resources.connections_empty
import org.opencairn.cairn.ui.design.resources.sync_event_failed
import org.opencairn.cairn.ui.design.resources.sync_event_imported
import org.opencairn.cairn.ui.design.resources.sync_event_started
import org.opencairn.cairn.ui.design.resources.sync_event_updated
import org.opencairn.cairn.ui.design.theme.CairnColors

/** The connected services, each with its sync state; a card opens the account's history and queue. */
@Composable
fun ConnectionsScreen(
    viewModel: ConnectionsViewModel,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(stringResource(Res.string.account_connections), onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            val accounts = state.accounts.value
            when {
                accounts == null && state.error == null -> {
                    LoadingBox()
                }

                accounts.isNullOrEmpty() -> {
                    EmptyBox(stringResource(Res.string.connections_empty))
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(accounts, key = { it.id }) { AccountCard(it, dates, onClick = { onOpen(it.id) }) }
                    }
                }
            }
        }
    }
}

/** One account: its state up top, then what is waiting and what was imported. */
@Composable
fun ConnectionDetailScreen(
    viewModel: ConnectionsViewModel,
    accountId: String,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account = state.account(accountId)
    SubPage(account?.displayName ?: stringResource(Res.string.account_connections), onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                account?.let { a -> item(key = "account") { AccountCard(a, dates, onClick = null) } }
                item(key = "queue-title") { SectionTitle(Res.string.connection_queue_title) }
                if (state.queue.isEmpty()) {
                    item(key = "queue-empty") { Muted(stringResource(Res.string.connection_queue_empty)) }
                }
                items(state.queue, key = { "q:${it.id}" }) { QueueRow(it, dates) }
                item(key = "history-title") { SectionTitle(Res.string.connection_history_title) }
                if (state.history.isEmpty()) {
                    item(key = "history-empty") { Muted(stringResource(Res.string.connection_history_empty)) }
                }
                items(state.history.size) { i -> HistoryRow(state.history[i], dates, onOpenActivity) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountCard(
    account: SyncAccount,
    dates: DateFormatters,
    onClick: (() -> Unit)?,
) {
    val open = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = Modifier.fillMaxWidth().then(open),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(account.status)
            }
            if (account.status == SyncAccountStatus.AUTH_INVALID) {
                Text(
                    stringResource(Res.string.connection_reauthorize_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = CairnColors.amber400,
                )
            }
            val imported =
                if (account.providerTotal > 0) {
                    stringResource(Res.string.connection_imported, account.imported, account.providerTotal)
                } else {
                    stringResource(Res.string.connection_imported_count, account.imported)
                }
            Text(imported, style = MaterialTheme.typography.bodyMedium)
            val lastSync =
                account.lastSuccessfulSyncAt?.let {
                    stringResource(
                        Res.string.connection_last_sync,
                        dates.relative(it),
                    )
                }
                    ?: stringResource(Res.string.connection_never_synced)
            val newest =
                account.newestActivityAt?.let {
                    stringResource(
                        Res.string.connection_newest,
                        dates.relative(it),
                    )
                }
            Muted(listOfNotNull(lastSync, newest).joinToString(" · "))
            val q = account.queue
            if (q.open > 0 || q.failed > 0) {
                Text(
                    stringResource(Res.string.connection_queue, q.pending, q.inProgress, q.failed),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (account.discovering) Muted(stringResource(Res.string.connection_discovering))
            if (!account.autoImport) Muted(stringResource(Res.string.connection_auto_import_off))
            account.shortWindow?.let { Budget(Res.string.connection_budget_short, it) }
            account.dailyWindow?.let { Budget(Res.string.connection_budget_daily, it) }
        }
    }
}

@Composable
private fun StatusBadge(status: SyncAccountStatus) {
    val color =
        when (status) {
            SyncAccountStatus.ACTIVE -> CairnColors.green400
            SyncAccountStatus.DISABLED, SyncAccountStatus.UNKNOWN -> CairnColors.zinc400
            SyncAccountStatus.AUTH_INVALID -> CairnColors.red400
            else -> CairnColors.amber400
        }
    Text(
        stringResource(status.label()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun SyncAccountStatus.label(): StringResource =
    when (this) {
        SyncAccountStatus.ACTIVE -> Res.string.connection_status_active
        SyncAccountStatus.AUTH_INVALID -> Res.string.connection_status_auth_invalid
        SyncAccountStatus.WORKER_OFFLINE -> Res.string.connection_status_worker_offline
        SyncAccountStatus.RATE_LIMITED -> Res.string.connection_status_rate_limited
        SyncAccountStatus.NEEDS_MIGRATION -> Res.string.connection_status_needs_migration
        SyncAccountStatus.DISABLED -> Res.string.connection_status_disabled
        SyncAccountStatus.UNKNOWN -> Res.string.connection_status_unknown
    }

/** How much of a provider's request budget is used; turns amber when nearly spent. */
@Composable
private fun Budget(
    title: StringResource,
    window: RateLimitWindow,
) {
    val share = (window.used.toFloat() / window.limit).coerceIn(0f, 1f)
    val color: Color = if (share >= 0.9f) CairnColors.amber400 else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(title), style = MaterialTheme.typography.labelMedium)
            Text("${window.used} / ${window.limit}", style = MaterialTheme.typography.labelMedium)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(modifier = Modifier.fillMaxWidth(share).height(6.dp).background(color))
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    dates: DateFormatters,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                listOf(item.type, item.externalId).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                item.status.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.status == "failed") CairnColors.red400 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val details =
            listOfNotNull(
                item.itemTime?.let(dates::date),
                stringResource(Res.string.connection_queue_attempts, item.attempts).takeIf { item.attempts > 1 },
                item.lastError.ifBlank { null },
            )
        if (details.isNotEmpty()) Muted(details.joinToString(" · "))
    }
    HorizontalDivider()
}

@Composable
private fun HistoryRow(
    event: SyncEvent,
    dates: DateFormatters,
    onOpenActivity: (String) -> Unit,
) {
    val activityId = event.activityId
    val open = if (activityId != null) Modifier.clickable { onOpenActivity(activityId) } else Modifier
    Row(modifier = open.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val title =
                when (event.kind) {
                    "sync_started" -> stringResource(Res.string.sync_event_started)
                    "activity_imported" -> stringResource(Res.string.sync_event_imported)
                    "activity_updated" -> stringResource(Res.string.sync_event_updated)
                    "failed" -> stringResource(Res.string.sync_event_failed)
                    else -> event.kind.replace('_', ' ')
                }
            Text(
                if (event.count > 1) "$title × ${event.count}" else title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (event.status == "failed") CairnColors.red400 else MaterialTheme.colorScheme.onSurface,
            )
            if (event.detail.isNotBlank()) Muted(event.detail)
        }
        event.occurredAt?.let {
            Text(
                dates.relative(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}
