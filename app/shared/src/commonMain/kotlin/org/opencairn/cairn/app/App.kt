package org.opencairn.cairn.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.server_title
import org.opencairn.cairn.ui.design.theme.CairnTheme

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Root composable shared by every platform entry point. */
@Composable
fun CairnApp(graph: AppGraph) {
    CompositionLocalProvider(LocalAppGraph provides graph) {
        CairnTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.server_title))
                }
            }
        }
    }
}
