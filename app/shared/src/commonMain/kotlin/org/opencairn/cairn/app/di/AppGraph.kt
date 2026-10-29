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
import org.opencairn.cairn.core.data.ActivityRepositoryImpl
import org.opencairn.cairn.core.data.CacheScope
import org.opencairn.cairn.core.data.OverviewRepositoryImpl
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.opencairn.cairn.core.database.appDatabase
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.domain.repository.SessionRepository

/** What each platform entry point hands to the shared graph. */
interface PlatformContext {
    val appVersion: String
    val databaseDriverFactory: DatabaseDriverFactory
    val authPlatform: AuthPlatform
}

/** Process-wide graph: one HttpClient, one database, one session; repositories scope their cache by account. */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val dispatchers: DispatcherProvider
    val platform: PlatformContext
    val httpClient: HttpClient
    val session: SessionManager
    val sessionRepository: SessionRepository
    val activityRepository: ActivityRepository
    val overviewRepository: OverviewRepository

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

    @Provides
    @SingleIn(AppScope::class)
    fun provideCacheScope(session: SessionManager): CacheScope = CacheScope(session)

    @Provides
    @SingleIn(AppScope::class)
    fun provideActivityRepository(
        db: CairnDb,
        scope: CacheScope,
        dispatchers: DispatcherProvider,
    ): ActivityRepository = ActivityRepositoryImpl(db, scope, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOverviewRepository(
        db: CairnDb,
        scope: CacheScope,
        dispatchers: DispatcherProvider,
    ): OverviewRepository = OverviewRepositoryImpl(db, scope, dispatchers)
}
