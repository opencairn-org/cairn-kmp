package org.opencairn.cairn.feature.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.AppRelease
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.model.SyncNotice
import org.opencairn.cairn.core.domain.model.Totals
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.feed_federated
import org.opencairn.cairn.ui.design.resources.feed_friends
import org.opencairn.cairn.ui.design.resources.feed_mine
import org.opencairn.cairn.ui.design.resources.overview_distance
import org.opencairn.cairn.ui.design.resources.overview_elevation
import org.opencairn.cairn.ui.design.resources.overview_feed
import org.opencairn.cairn.ui.design.resources.overview_feed_empty
import org.opencairn.cairn.ui.design.resources.overview_last_week
import org.opencairn.cairn.ui.design.resources.overview_moving_time
import org.opencairn.cairn.ui.design.resources.overview_this_week
import org.opencairn.cairn.ui.design.resources.overview_total_activities
import org.opencairn.cairn.ui.design.resources.overview_welcome
import org.opencairn.cairn.ui.design.resources.sync_notice_details
import org.opencairn.cairn.ui.design.resources.sync_notice_discovering
import org.opencairn.cairn.ui.design.resources.sync_notice_importing
import org.opencairn.cairn.ui.design.resources.sync_notice_offline
import org.opencairn.cairn.ui.design.resources.sync_notice_rate_limited
import org.opencairn.cairn.ui.design.resources.sync_notice_sign_in
import org.opencairn.cairn.ui.design.resources.update_available
import org.opencairn.cairn.ui.design.resources.update_dismiss
import org.opencairn.cairn.ui.design.resources.update_open
import org.opencairn.cairn.ui.design.theme.CairnColors

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    federationEnabled: Boolean,
    actions: OverviewActions,
    update: AppRelease? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overview = state.overview.value
    if (overview == null && state.refreshing) {
        LoadingBox()
        return
    }
    Column {
        if (state.error != null) StaleBanner(state.error, onRetry = viewModel::refresh)
        LazyColumn(
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(Res.string.overview_welcome), style = MaterialTheme.typography.headlineSmall) }
            update?.let { release -> item(key = "update") { UpdateCard(release, actions) } }
            state.syncNotice?.let { notice -> item(key = "sync") { SyncNoticeCard(notice, actions.onOpenConnections) } }
            if (overview != null) {
                item { TotalsGrid(overview.totals, formatters) }
                item { WeekCard(stringResource(Res.string.overview_this_week), overview.thisWeek, formatters) }
                item { WeekCard(stringResource(Res.string.overview_last_week), overview.lastWeek, formatters) }
            }
            item {
                Text(stringResource(Res.string.overview_feed), style = MaterialTheme.typography.titleMedium)
                FeedChips(state.feedFilter, federationEnabled, viewModel::setFeedFilter)
            }
            val feed = state.visibleFeed
            if (feed.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.overview_feed_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(feed, key = { it.source.name + it.id }) { item ->
                FeedCard(item, formatters, dates, onClick = {
                    if (item.source ==
                        FeedItem.Source.SELF
                    ) {
                        actions.onOpenActivity(item.id)
                    }
                })
            }
        }
    }
}

@Composable
private fun TotalsGrid(
    t: Totals,
    f: Formatters,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_total_activities), t.count.toString(), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_distance), f.distance(t.distance), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_moving_time), f.duration(t.moving), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_elevation), f.elevation(t.elevationGain), Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekCard(
    title: String,
    t: Totals,
    f: Formatters,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.overview_total_activities), t.count.toString(), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_distance), f.distance(t.distance), Modifier.weight(1f))
            StatTile(stringResource(Res.string.overview_moving_time), f.duration(t.moving), Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeedChips(
    selected: FeedItem.Source?,
    federation: Boolean,
    onSelect: (FeedItem.Source?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == FeedItem.Source.SELF, onClick = {
            onSelect(
                FeedItem.Source.SELF.takeIf {
                    selected !=
                        it
                },
            )
        }, label = { Text(stringResource(Res.string.feed_mine)) })
        FilterChip(selected = selected == FeedItem.Source.FOLLOWING, onClick = {
            onSelect(
                FeedItem.Source.FOLLOWING.takeIf {
                    selected !=
                        it
                },
            )
        }, label = { Text(stringResource(Res.string.feed_friends)) })
        if (federation) {
            FilterChip(selected = selected == FeedItem.Source.FEDERATED, onClick = {
                onSelect(
                    FeedItem.Source.FEDERATED.takeIf {
                        selected !=
                            it
                    },
                )
            }, label = { Text(stringResource(Res.string.feed_federated)) })
        }
    }
}

/** One line about syncing; a lost sign-in is a warning, the rest is information. */
@Composable
private fun SyncNoticeCard(
    notice: SyncNotice,
    onDetails: () -> Unit,
) {
    val warning = notice.kind == SyncNotice.Kind.NEEDS_SIGN_IN
    val text =
        when (notice.kind) {
            SyncNotice.Kind.NEEDS_SIGN_IN -> {
                stringResource(Res.string.sync_notice_sign_in, notice.account)
            }

            SyncNotice.Kind.IMPORTING -> {
                stringResource(
                    Res.string.sync_notice_importing,
                    notice.account,
                    notice.pending,
                )
            }

            SyncNotice.Kind.DISCOVERING -> {
                stringResource(Res.string.sync_notice_discovering, notice.account)
            }

            SyncNotice.Kind.RATE_LIMITED -> {
                stringResource(Res.string.sync_notice_rate_limited, notice.account)
            }

            SyncNotice.Kind.WORKER_OFFLINE -> {
                stringResource(Res.string.sync_notice_offline, notice.account)
            }
        }
    Card(
        onClick = onDetails,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (warning) Icons.Default.Warning else Icons.Default.Sync,
                contentDescription = null,
                tint = if (warning) CairnColors.amber400 else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDetails) { Text(stringResource(Res.string.sync_notice_details)) }
        }
    }
}

/** A newer release: where to read about it, and a way to make the card go away until the next one. */
@Composable
private fun UpdateCard(
    release: AppRelease,
    actions: OverviewActions,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.update_available, release.version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { actions.onOpenUpdate(release) }) { Text(stringResource(Res.string.update_open)) }
            IconButton(onClick = { actions.onDismissUpdate(release) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.update_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
