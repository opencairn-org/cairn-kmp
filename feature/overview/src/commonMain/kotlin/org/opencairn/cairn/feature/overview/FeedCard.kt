package org.opencairn.cairn.feature.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters

@Composable
fun FeedCard(
    item: FeedItem,
    f: Formatters,
    dates: DateFormatters,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportIcon(item.type, Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title.ifBlank { item.sport }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    val who = item.owner?.displayName?.let { "$it · " } ?: ""
                    Text(
                        who + dates.relative(item.startTime) +
                            item.startPlace
                                .takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            item.mapImageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item.distance?.let { Text(f.distance(it), style = MaterialTheme.typography.bodyMedium) }
                item.elapsed?.let { Text(f.duration(it), style = MaterialTheme.typography.bodyMedium) }
                item.elevationGain?.let { Text(f.elevation(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
