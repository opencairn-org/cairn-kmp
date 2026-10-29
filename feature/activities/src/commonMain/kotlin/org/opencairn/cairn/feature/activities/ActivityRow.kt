package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.ui.design.components.SportIcon
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters

/** One row of the activity list: icon, title, when/where, key numbers, map thumbnail. */
@Composable
fun ActivityRow(
    a: Activity,
    f: Formatters,
    dates: DateFormatters,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SportIcon(a.type, Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val where = a.startPlace.ifBlank { a.timezone }
            Text(
                "${dates.relative(a.startTime)} · $where",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                a.summary.distance?.let { Text(f.distance(it), style = MaterialTheme.typography.bodySmall) }
                Text(
                    f.duration(a.moving.takeIf { it.inWholeSeconds > 0 } ?: a.elapsed),
                    style = MaterialTheme.typography.bodySmall,
                )
                a.summary.elevationGain?.let { Text(f.elevation(it), style = MaterialTheme.typography.bodySmall) }
                a.summary.tss?.let { Text("TSS ${it.toInt()}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        a.mapImageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}
