package org.opencairn.cairn.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.ui.charts.EffortBars
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.StatTile
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.records_history_all
import org.opencairn.cairn.ui.design.resources.records_history_best
import org.opencairn.cairn.ui.design.resources.records_history_count
import org.opencairn.cairn.ui.design.resources.records_history_empty

/** One record over time: best and count up top, a bar per activity (the best highlighted), then the list. */
@Composable
fun RecordHistoryScreen(
    viewModel: RecordHistoryViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val key = viewModel.key
    val title = "${key.activityType.label()} · ${formatters.effortLabel(
        key.metric,
    )} · ${formatters.effortWindow(key.windowKind, key.windowValue)}"
    SubPage(title = title, onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            val history = state.history.value
            when {
                history == null -> {
                    LoadingBox()
                }

                history.items.isEmpty() -> {
                    EmptyBox(stringResource(Res.string.records_history_empty))
                }

                else -> {
                    LazyColumn {
                        item {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatTile(
                                    stringResource(Res.string.records_history_best),
                                    formatters.effortValue(key.metric, history.best),
                                    Modifier.weight(1f),
                                )
                                StatTile(
                                    stringResource(Res.string.records_history_count),
                                    history.count.toString(),
                                    Modifier.weight(1f),
                                )
                            }
                        }
                        item {
                            EffortBars(
                                values = history.items.map { it.achievedValue },
                                best = history.items.indexOfFirst { it.isBest },
                                lowerIsBetter =
                                    key.metric == org.opencairn.cairn.core.domain.model.BestEffortMetric.PACE,
                                modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp),
                            )
                        }
                        item {
                            Text(
                                stringResource(Res.string.records_history_all),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(history.items.asReversed(), key = { it.activityId + it.startTime }) { item ->
                            ListItem(
                                headlineContent = { Text(item.title.ifBlank { item.activityId }) },
                                supportingContent = item.startTime?.let { { Text(dates.date(it)) } },
                                leadingContent =
                                    if (item.isBest) {
                                        {
                                            Icon(
                                                Icons.Default.EmojiEvents,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                trailingContent = {
                                    Text(
                                        formatters.effortValue(key.metric, item.achievedValue),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                modifier = Modifier.clickable { onOpenActivity(item.activityId) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
