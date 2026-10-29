package org.opencairn.cairn.feature.activities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.ui.design.components.EmptyBox
import org.opencairn.cairn.ui.design.components.LoadingBox
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.detail_no_track
import org.opencairn.cairn.ui.map.RouteMap

/** Full-height route map; shares the detail view model so the cursor position carries over. */
@Composable
fun ActivityMapScreen(viewModel: ActivityDetailViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stream = state.stream.value
    when {
        stream == null && state.refreshing -> LoadingBox()
        stream == null || !stream.hasGps -> EmptyBox(stringResource(Res.string.detail_no_track))
        else -> Box(modifier = Modifier.fillMaxSize()) { RouteMap(stream, state.selectedIndex) }
    }
}
