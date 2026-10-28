package org.opencairn.cairn.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import org.opencairn.cairn.app.CairnApp
import org.opencairn.cairn.app.di.AppGraph

fun main() {
    val graph = createGraphFactory<AppGraph.Factory>().create(DesktopPlatformContext)
    application {
        Window(onCloseRequest = ::exitApplication, title = "Cairn") {
            CairnApp(graph)
        }
    }
}
