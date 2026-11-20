package org.opencairn.cairn.app.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.SessionManager
import org.opencairn.cairn.core.common.DefaultDispatcherProvider
import org.opencairn.cairn.core.common.DispatcherProvider
import org.opencairn.cairn.core.data.ActivityInsightsRepositoryImpl
import org.opencairn.cairn.core.data.ActivityRepositoryImpl
import org.opencairn.cairn.core.data.AnalyticsRepositoryImpl
import org.opencairn.cairn.core.data.AthleteRepositoryImpl
import org.opencairn.cairn.core.data.CacheScope
import org.opencairn.cairn.core.data.DeviceApprovalRepositoryImpl
import org.opencairn.cairn.core.data.GearRepositoryImpl
import org.opencairn.cairn.core.data.LiveEventsRepositoryImpl
import org.opencairn.cairn.core.data.NotificationRepositoryImpl
import org.opencairn.cairn.core.data.OverviewRepositoryImpl
import org.opencairn.cairn.core.data.RecordsRepositoryImpl
import org.opencairn.cairn.core.data.SnapshotStore
import org.opencairn.cairn.core.data.SyncRepositoryImpl
import org.opencairn.cairn.core.data.UpdateRepositoryImpl
import org.opencairn.cairn.core.database.CairnDb
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.opencairn.cairn.core.database.appDatabase
import org.opencairn.cairn.core.domain.repository.ActivityInsightsRepository
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.core.domain.repository.DeviceApprovalRepository
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.core.domain.repository.LiveEventsRepository
import org.opencairn.cairn.core.domain.repository.NotificationRepository
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import org.opencairn.cairn.core.domain.repository.SessionRepository
import org.opencairn.cairn.core.domain.repository.SyncRepository
import org.opencairn.cairn.core.domain.repository.UpdateRepository

/** What each platform entry point hands to the shared graph. */
interface PlatformContext {
    val appVersion: String
    val databaseDriverFactory: DatabaseDriverFactory
    val authPlatform: AuthPlatform

    /** True for builds the user updates by hand from the release page; they get the update notice. */
    val updateNotice: Boolean get() = false
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
    val deviceApprovalRepository: DeviceApprovalRepository
    val athleteRepository: AthleteRepository
    val gearRepository: GearRepository
    val recordsRepository: RecordsRepository
    val insightsRepository: ActivityInsightsRepository
    val analyticsRepository: AnalyticsRepository
    val syncRepository: SyncRepository
    val notificationRepository: NotificationRepository
    val liveEvents: LiveEventsRepository
    val updateRepository: UpdateRepository

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
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): OverviewRepository = OverviewRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDeviceApprovalRepository(scope: CacheScope): DeviceApprovalRepository =
        DeviceApprovalRepositoryImpl(scope)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSnapshotStore(
        db: CairnDb,
        scope: CacheScope,
        dispatchers: DispatcherProvider,
    ): SnapshotStore = SnapshotStore(db, scope, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAthleteRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): AthleteRepository = AthleteRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGearRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): GearRepository = GearRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRecordsRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): RecordsRepository = RecordsRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideInsightsRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): ActivityInsightsRepository = ActivityInsightsRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalyticsRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
        dispatchers: DispatcherProvider,
    ): AnalyticsRepository = AnalyticsRepositoryImpl(scope, snapshots, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSyncRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): SyncRepository = SyncRepositoryImpl(scope, snapshots)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNotificationRepository(
        scope: CacheScope,
        snapshots: SnapshotStore,
    ): NotificationRepository = NotificationRepositoryImpl(scope, snapshots)

    /** The streams live as long as the process; they only hold a connection while a screen listens. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideLiveEvents(
        scope: CacheScope,
        dispatchers: DispatcherProvider,
    ): LiveEventsRepository = LiveEventsRepositoryImpl(scope, CoroutineScope(SupervisorJob() + dispatchers.default))

    @Provides
    @SingleIn(AppScope::class)
    fun provideUpdateRepository(
        http: HttpClient,
        db: CairnDb,
        platform: PlatformContext,
        dispatchers: DispatcherProvider,
    ): UpdateRepository = UpdateRepositoryImpl(http, db, dispatchers, platform.appVersion, platform.updateNotice)
}
