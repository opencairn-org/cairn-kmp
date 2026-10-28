package org.opencairn.cairn.app.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.SessionManager
import org.opencairn.cairn.core.common.DefaultDispatcherProvider
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.opencairn.cairn.core.database.appDatabase
import org.opencairn.cairn.core.domain.repository.SessionRepository

/** What each platform entry point hands to the shared graph. */
interface PlatformContext {
    val appVersion: String
    val databaseDriverFactory: DatabaseDriverFactory
    val authPlatform: AuthPlatform
}

/** Process-wide graph; account-scoped children hang off it once an account is active. */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val dispatchers: DispatcherProvider
    val platform: PlatformContext
    val session: SessionManager
    val sessionRepository: SessionRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides platform: PlatformContext,
        ): AppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient =
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 60_000
            }
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(platform: PlatformContext): CairnDb = platform.databaseDriverFactory.appDatabase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSession(
        http: HttpClient,
        db: CairnDb,
        platform: PlatformContext,
        dispatchers: DispatcherProvider,
    ): SessionManager = SessionManager(http, db, platform.authPlatform, dispatchers, platform.appVersion)

    @Provides
    fun provideSessionRepository(session: SessionManager): SessionRepository = session
}
