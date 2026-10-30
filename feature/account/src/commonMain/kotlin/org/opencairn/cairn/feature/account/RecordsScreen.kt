package org.opencairn.cairn.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_records
import org.opencairn.cairn.ui.design.resources.records_empty

fun PersonalRecord.key() = RecordKey(activityType, metric, windowKind, windowValue)

/** Personal records grouped by sport; tapping one shows how it developed over the activities. */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    formatters: Formatters,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpen: (RecordKey) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(title = stringResource(Res.string.account_records), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            val records = state.records.value
            when {
                records == null -> {
                    LoadingBox()
                }

                records.isEmpty() -> {
                    EmptyBox(stringResource(Res.string.records_empty))
                }

                else -> {
                    LazyColumn {
                        state.groups.forEach { (type, list) ->
                            item(key = "h:${type.wire}") {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SportIcon(type, Modifier.size(20.dp))
                                    Text(type.label(), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            items(list, key = { "${it.key()}" }) { r ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            "${formatters.effortLabel(
                                                r.metric,
                                            )} · ${formatters.effortWindow(r.windowKind, r.windowValue)}",
                                        )
                                    },
                                    supportingContent = r.achievedAt?.let { { Text(dates.date(it)) } },
                                    trailingContent = {
                                        Text(
                                            formatters.effortValue(r.metric, r.achievedValue),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    },
                                    modifier = Modifier.clickable { onOpen(r.key()) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
