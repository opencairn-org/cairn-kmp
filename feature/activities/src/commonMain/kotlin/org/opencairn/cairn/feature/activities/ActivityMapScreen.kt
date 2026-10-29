package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.detail_map
import org.opencairn.cairn.ui.design.resources.detail_no_track
import org.opencairn.cairn.ui.map.RouteMap

/** Full-height route map; shares the detail view model so the cursor position carries over. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityMapScreen(
    viewModel: ActivityDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stream = state.stream.value
    ActivityPage(state.activity.value, onBack, subtitle = Res.string.detail_map) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                stream == null && state.refreshing -> LoadingBox()
                stream == null || !stream.hasGps -> EmptyBox(stringResource(Res.string.detail_no_track))
                else -> RouteMap(stream, state.selectedIndex)
            }
        }
    }
}
