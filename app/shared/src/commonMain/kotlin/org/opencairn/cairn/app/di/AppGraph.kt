package org.opencairn.cairn.app.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import org.opencairn.cairn.core.common.DefaultDispatcherProvider
import org.opencairn.cairn.core.common.DispatcherProvider

/** Platform services each app module provides to the shared graph. */
interface PlatformContext

/** Process-wide graph; account-scoped children hang off it once an account is active. */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val dispatchers: DispatcherProvider
    val platform: PlatformContext

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides platform: PlatformContext,
        ): AppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()
}
