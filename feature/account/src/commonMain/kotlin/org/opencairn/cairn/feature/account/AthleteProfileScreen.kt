package org.opencairn.cairn.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_athlete
import org.opencairn.cairn.ui.design.resources.account_athlete_hint
import org.opencairn.cairn.ui.design.resources.account_no_value

/** Every parameter the server knows with its current value; tapping one opens its history. */
@Composable
fun AthleteProfileScreen(
    viewModel: AthleteViewModel,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpenMetric: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(title = stringResource(Res.string.account_athlete), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            val profile = state.profile.value
            if (profile == null) {
                LoadingBox()
                return@Column
            }
            LazyColumn {
                item {
                    Text(
                        stringResource(Res.string.account_athlete_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(profile.specs, key = { it.key }) { spec ->
                    val latest = profile.latest(spec.key)
                    ListItem(
                        headlineContent = { Text(spec.label) },
                        supportingContent = latest?.let { { Text(dates.isoDate(it.effectiveDate)) } },
                        trailingContent = {
                            Text(
                                latest?.let { spec.format(it.value) } ?: stringResource(Res.string.account_no_value),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        modifier = Modifier.clickable { onOpenMetric(spec.key) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
