package org.opencairn.cairn.feature.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
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

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    federationEnabled: Boolean,
    onOpenActivity: (String) -> Unit,
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
                        onOpenActivity(item.id)
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
