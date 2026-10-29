package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.activities_empty
import org.opencairn.cairn.ui.design.resources.activities_load_more
import org.opencairn.cairn.ui.design.resources.activities_matched
import org.opencairn.cairn.ui.design.resources.activities_title
import org.opencairn.cairn.ui.design.resources.filter_title

@Composable
fun ActivitiesScreen(
    viewModel: ActivitiesViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    onOpen: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.page
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, page?.hasMore) { if (nearEnd && page?.hasMore == true) viewModel.loadMore() }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(Res.string.activities_title), style = MaterialTheme.typography.headlineSmall)
                page?.let {
                    Text(
                        stringResource(Res.string.activities_matched, it.matched, it.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = viewModel::toggleFilters) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(Res.string.filter_title))
            }
        }
        if (state.showFilters) {
            FilterPanel(
                state.filter,
                state.sort,
                page?.facets,
                viewModel::setFilter,
                viewModel::setSort,
                viewModel::clearFilter,
            )
            HorizontalDivider()
        }
        if (state.error != null) StaleBanner(state.error, onRetry = viewModel::refresh)
        when {
            page == null && state.refreshing -> {
                LoadingBox()
            }

            page == null || page.activities.isEmpty() -> {
                EmptyBox(stringResource(Res.string.activities_empty))
            }

            else -> {
                LazyColumn(state = listState) {
                    items(page.activities, key = { it.id }) { a ->
                        ActivityRow(a, formatters, dates, onClick = { onOpen(a.id) })
                        HorizontalDivider()
                    }
                    if (page.hasMore) {
                        item {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.loadingMore,
                            ) {
                                Text(stringResource(Res.string.activities_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}
