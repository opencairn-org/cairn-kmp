package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.ui.charts.StreamCharts
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.detail_no_streams

/** Every channel, taller, one cursor; the web's /streams page. */
@Composable
fun ActivityStreamsScreen(
    viewModel: ActivityDetailViewModel,
    units: Units,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stream = state.stream.value
    when {
        stream == null && state.refreshing -> {
            LoadingBox()
        }

        stream == null || stream.channels.isEmpty() -> {
            EmptyBox(stringResource(Res.string.detail_no_streams))
        }

        else -> {
            // The channels share the screen height; scrolling only kicks in below the minimum chart height.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val labels = LABEL_HEIGHT * stream.channels.size
                val height = ((maxHeight - PADDING * 2 - labels) / stream.channels.size).coerceAtLeast(MIN_CHART)
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PADDING)) {
                    StreamCharts(stream, units, state.selectedIndex, viewModel::select, height = height)
                }
            }
        }
    }
}

private val PADDING = 16.dp
private val MIN_CHART = 160.dp
private val LABEL_HEIGHT = 24.dp
