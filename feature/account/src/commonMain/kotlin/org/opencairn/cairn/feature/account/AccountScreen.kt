package org.opencairn.cairn.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_about
import org.opencairn.cairn.ui.design.resources.account_athlete
import org.opencairn.cairn.ui.design.resources.account_gear
import org.opencairn.cairn.ui.design.resources.account_gear_count
import org.opencairn.cairn.ui.design.resources.account_no_value
import org.opencairn.cairn.ui.design.resources.account_records
import org.opencairn.cairn.ui.design.resources.account_records_count
import org.opencairn.cairn.ui.design.resources.account_settings
import org.opencairn.cairn.ui.design.resources.sign_out

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    user: User?,
    dates: DateFormatters,
    actions: AccountActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item { Header(user) }
            item { AthleteCard(state, dates, actions.onAthlete) }
            item {
                NavRow(
                    Icons.Default.PedalBike,
                    stringResource(Res.string.account_gear),
                    stringResource(Res.string.account_gear_count, state.activeGearCount),
                    actions.onGear,
                )
            }
            item {
                NavRow(
                    Icons.Default.EmojiEvents,
                    stringResource(Res.string.account_records),
                    stringResource(Res.string.account_records_count, state.recordCount),
                    actions.onRecords,
                )
            }
            item {
                NavRow(
                    Icons.Default.Settings,
                    stringResource(Res.string.account_settings),
                    null,
                    actions.onSettings,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = actions.onAbout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.account_about))
                    }
                    OutlinedButton(onClick = actions.onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(user: User?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(user?.avatarUrl, 64.dp)
        Column {
            Text(user?.displayName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            Text(
                "@${user?.username.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                user?.email.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The user's picture or a placeholder; also used by the bottom bar. */
@Composable
fun Avatar(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = modifier.size(size))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    }
}

@Composable
private fun AthleteCard(
    state: AccountUiState,
    dates: DateFormatters,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null)
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(Res.string.account_athlete),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            state.highlights.forEach { (spec, entry) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            entry?.let {
                                spec.format(it.value)
                            } ?: stringResource(Res.string.account_no_value),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        entry?.let {
                            Text(
                                dates.isoDate(it.effectiveDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
