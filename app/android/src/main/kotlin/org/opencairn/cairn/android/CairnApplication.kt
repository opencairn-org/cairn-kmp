package org.opencairn.cairn.android

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.app.di.PlatformContext

class CairnApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(AndroidPlatformContext(this))
    }
}

class AndroidPlatformContext(
    val application: Application,
) : PlatformContext
