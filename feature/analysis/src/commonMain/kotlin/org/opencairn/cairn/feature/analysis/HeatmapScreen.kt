package org.opencairn.cairn.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.StaleBanner
import org.opencairn.cairn.ui.design.components.SubPage
import org.opencairn.cairn.ui.design.components.label
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.analysis_heatmap
import org.opencairn.cairn.ui.design.resources.filter_all
import org.opencairn.cairn.ui.design.resources.heatmap_empty
import org.opencairn.cairn.ui.design.resources.heatmap_summary
import org.opencairn.cairn.ui.design.resources.heatmap_truncated
import org.opencairn.cairn.ui.map.HeatmapMap

/** Every GPS track of the selected sport on one map. */
@Composable
fun HeatmapScreen(
    viewModel: HeatmapViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SubPage(stringResource(Res.string.analysis_heatmap), onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            state.error?.let { StaleBanner(it, onRetry = { viewModel.load() }) }
            if (state.sports.size > 1) SportChips(state.sports, state.type, viewModel::setType)
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.heatmap?.let { heatmap ->
                Summary(heatmap.withTrack, heatmap.matched, heatmap.truncated)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (heatmap.tracks.isEmpty()) {
                        EmptyBox(stringResource(Res.string.heatmap_empty))
                    } else {
                        HeatmapMap(heatmap.tracks)
                    }
                }
            }
        }
    }
}

@Composable
private fun SportChips(
    sports: List<ActivityType>,
    selected: ActivityType?,
    onSelect: (ActivityType?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.filter_all)) },
            )
        }
        items(sports, key = { it.name }) { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(type.label()) })
        }
    }
}

@Composable
private fun Summary(
    withTrack: Int,
    matched: Int,
    truncated: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            stringResource(Res.string.heatmap_summary, withTrack, matched),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (truncated) {
            Text(
                stringResource(Res.string.heatmap_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
