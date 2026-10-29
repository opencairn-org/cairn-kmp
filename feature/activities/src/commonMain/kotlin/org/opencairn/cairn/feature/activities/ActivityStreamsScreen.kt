package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.StreamChannel
import org.opencairn.cairn.core.domain.model.Units
import org.opencairn.cairn.ui.charts.StreamCharts
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.detail_no_streams
import org.opencairn.cairn.ui.design.resources.detail_streams

/** Every channel, taller, one cursor; the web's /streams page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityStreamsScreen(
    viewModel: ActivityDetailViewModel,
    units: Units,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stream = state.stream.value
    val channels = stream?.channels.orEmpty().filterNot { it in state.hiddenChannels }
    ActivityPage(state.activity.value, onBack, subtitle = Res.string.detail_streams) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { Streams(viewModel, state, stream, channels, units) }
    }
}

@Composable
private fun Streams(
    viewModel: ActivityDetailViewModel,
    state: ActivityDetailUiState,
    stream: ActivityStream?,
    channels: List<StreamChannel>,
    units: Units,
) {
    when {
        stream == null && state.refreshing -> {
            LoadingBox()
        }

        stream == null || channels.isEmpty() -> {
            EmptyBox(stringResource(Res.string.detail_no_streams))
        }

        else -> {
            // The channels share the screen height; scrolling only kicks in below the minimum chart height.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val labels = LABEL_HEIGHT * channels.size
                val height = ((maxHeight - PADDING * 2 - labels) / channels.size).coerceAtLeast(MIN_CHART)
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PADDING)) {
                    StreamCharts(
                        stream,
                        units,
                        state.selectedIndex,
                        viewModel::select,
                        height = height,
                        channels = channels,
                    )
                }
            }
        }
    }
}

private val PADDING = 16.dp
private val MIN_CHART = 160.dp
private val LABEL_HEIGHT = 24.dp
