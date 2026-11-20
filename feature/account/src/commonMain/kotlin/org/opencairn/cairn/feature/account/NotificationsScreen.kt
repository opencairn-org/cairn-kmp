package org.opencairn.cairn.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.NotificationSeverity
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.account_notifications
import org.opencairn.cairn.ui.design.resources.notifications_empty
import org.opencairn.cairn.ui.design.resources.notifications_mark_all
import org.opencairn.cairn.ui.design.resources.notifications_unread_only
import org.opencairn.cairn.ui.design.theme.CairnColors

/** Tapping a notification marks it read and, when it is about an activity, opens that activity. */
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    dates: DateFormatters,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(
        stringResource(Res.string.account_notifications),
        onBack,
        actions = {
            if (state.unread.total > 0) {
                IconButton(onClick = viewModel::markAllRead) {
                    Icon(Icons.Default.DoneAll, contentDescription = stringResource(Res.string.notifications_mark_all))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.error?.let { StaleBanner(it, onRetry = viewModel::refresh) }
            FilterChip(
                selected = state.unreadOnly,
                onClick = { viewModel.setUnreadOnly(!state.unreadOnly) },
                label = { Text(stringResource(Res.string.notifications_unread_only)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val visible = state.visible
            if (visible.isEmpty()) {
                if (!state.refreshing) EmptyBox(stringResource(Res.string.notifications_empty))
            } else {
                LazyColumn {
                    items(visible, key = { it.id }) { n ->
                        NotificationRow(n, dates) {
                            if (!n.read) viewModel.markRead(n.id)
                            n.activityId?.let(onOpenActivity)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    n: Notification,
    dates: DateFormatters,
    onClick: () -> Unit,
) {
    val color =
        when (n.severity) {
            NotificationSeverity.ERROR -> CairnColors.red400
            NotificationSeverity.WARN -> CairnColors.amber400
            NotificationSeverity.INFO -> MaterialTheme.colorScheme.primary
        }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The dot is filled while unread and fades with the entry once read.
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(if (n.read) color.copy(alpha = 0.25f) else color, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (n.count > 1) "${n.title} × ${n.count}" else n.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (n.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                n.params.entries.sortedBy { it.key }.joinToString(
                    " · ",
                ) { "${it.key.replace('_', ' ')}: ${it.value}" }
            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        n.createdAt?.let {
            Text(
                dates.relative(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}
