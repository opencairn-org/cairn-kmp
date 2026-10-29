package org.opencairn.cairn.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost
import org.opencairn.cairn.app.CairnApp
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.app_icon

fun main() {
    val graph = createGraphFactory<AppGraph.Factory>().create(DesktopPlatformContext)
    application {
        Window(onCloseRequest = ::exitApplication, title = "Cairn", icon = painterResource(Res.drawable.app_icon)) {
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                CairnApp(graph)
            }
        }
    }
}
