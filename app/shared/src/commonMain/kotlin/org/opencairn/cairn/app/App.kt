package org.opencairn.cairn.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.app.navigation.Route
import org.opencairn.cairn.app.navigation.topLevelRoutes
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.BestEffortMetric
import org.opencairn.cairn.core.domain.model.BestEffortWindowKind
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.feature.account.AboutScreen
import org.opencairn.cairn.feature.account.AccountActions
import org.opencairn.cairn.feature.account.AccountScreen
import org.opencairn.cairn.feature.account.AccountViewModel
import org.opencairn.cairn.feature.account.AthleteProfileScreen
import org.opencairn.cairn.feature.account.AthleteViewModel
import org.opencairn.cairn.feature.account.Avatar
import org.opencairn.cairn.feature.account.ConnectionDetailScreen
import org.opencairn.cairn.feature.account.ConnectionsScreen
import org.opencairn.cairn.feature.account.ConnectionsViewModel
import org.opencairn.cairn.feature.account.GearDetailScreen
import org.opencairn.cairn.feature.account.GearEditScreen
import org.opencairn.cairn.feature.account.GearEditViewModel
import org.opencairn.cairn.feature.account.GearScreen
import org.opencairn.cairn.feature.account.GearViewModel
import org.opencairn.cairn.feature.account.MetricHistoryScreen
import org.opencairn.cairn.feature.account.NotificationsScreen
import org.opencairn.cairn.feature.account.NotificationsViewModel
import org.opencairn.cairn.feature.account.RecordHistoryScreen
import org.opencairn.cairn.feature.account.RecordHistoryViewModel
import org.opencairn.cairn.feature.account.RecordsScreen
import org.opencairn.cairn.feature.account.RecordsViewModel
import org.opencairn.cairn.feature.account.SettingsScreen
import org.opencairn.cairn.feature.account.SettingsViewModel
import org.opencairn.cairn.feature.account.UpdateSetting
import org.opencairn.cairn.feature.activities.ActivitiesScreen
import org.opencairn.cairn.feature.activities.ActivitiesViewModel
import org.opencairn.cairn.feature.activities.ActivityDetailActions
import org.opencairn.cairn.feature.activities.ActivityDetailScreen
import org.opencairn.cairn.feature.activities.ActivityDetailViewModel
import org.opencairn.cairn.feature.activities.ActivityMapScreen
import org.opencairn.cairn.feature.activities.ActivityStreamsScreen
import org.opencairn.cairn.feature.analysis.AnalysisScreen
import org.opencairn.cairn.feature.analysis.AnalysisViewModel
import org.opencairn.cairn.feature.analysis.HeatmapScreen
import org.opencairn.cairn.feature.analysis.HeatmapViewModel
import org.opencairn.cairn.feature.analysis.StatsScreen
import org.opencairn.cairn.feature.analysis.StatsViewModel
import org.opencairn.cairn.feature.login.ApproveDeviceScreen
import org.opencairn.cairn.feature.login.ApproveDeviceViewModel
import org.opencairn.cairn.feature.login.LoginScreen
import org.opencairn.cairn.feature.login.LoginViewModel
import org.opencairn.cairn.feature.overview.OverviewActions
import org.opencairn.cairn.feature.overview.OverviewScreen
import org.opencairn.cairn.feature.overview.OverviewViewModel
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.nav_account
import org.opencairn.cairn.ui.design.resources.nav_activities
import org.opencairn.cairn.ui.design.resources.nav_analysis
import org.opencairn.cairn.ui.design.resources.nav_overview
import org.opencairn.cairn.ui.design.theme.CairnTheme

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Lets the back stack survive process death: every Route subclass is registered for NavKey. */
private val navSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Overview::class)
                    subclass(Route.Activities::class)
                    subclass(Route.Analysis::class)
                    subclass(Route.Stats::class)
                    subclass(Route.Heatmap::class)
                    subclass(Route.Account::class)
                    subclass(Route.AthleteProfile::class)
                    subclass(Route.MetricHistory::class)
                    subclass(Route.Gear::class)
                    subclass(Route.GearDetail::class)
                    subclass(Route.GearEdit::class)
                    subclass(Route.Records::class)
                    subclass(Route.RecordHistory::class)
                    subclass(Route.Notifications::class)
                    subclass(Route.Connections::class)
                    subclass(Route.ConnectionDetail::class)
                    subclass(Route.Settings::class)
                    subclass(Route.About::class)
                    subclass(Route.ApproveDevice::class)
                    subclass(Route.ActivityDetail::class)
                    subclass(Route.ActivityMap::class)
                    subclass(Route.ActivityStreams::class)
                }
            }
    }

/** Root composable shared by every platform entry point: signed-out → login, else the app shell. */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
fun CairnApp(graph: AppGraph) {
    setSingletonImageLoaderFactory { context ->
        // Signed URLs are fetched without credentials; the shared client only supplies the engine.
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory(graph.httpClient)) }.build()
    }
    CompositionLocalProvider(LocalAppGraph provides graph) {
        CairnTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val user by graph.sessionRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
                val current = user
                if (current == null) {
                    val vm = viewModel { LoginViewModel(graph.sessionRepository) }
                    LoginScreen(vm)
                } else {
                    AppShell(graph, current.preferences)
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    graph: AppGraph,
    prefs: Preferences,
) {
    val formatters = remember(prefs) { Formatters(prefs.units) }
    val dates = remember(prefs) { DateFormatters(prefs) }
    val backStack = rememberNavBackStack(navSavedStateConfiguration, Route.Overview)
    val user by graph.sessionRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val account by graph.sessionRepository.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    // One view model for the badge and the notification page: both follow the same list, and the
    // badge keeps the notification stream open for as long as the app is on screen.
    val notifications =
        viewModel(key = "notifications") {
            NotificationsViewModel(graph.notificationRepository, graph.liveEvents.notificationArrivals)
        }
    val unread by notifications.state.collectAsStateWithLifecycle()
    val top = backStack.lastOrNull()
    // Sub-pages own their top bar, which draws under the status bar; the shell only pads the bottom.
    val insets = if (top in topLevelRoutes) ScaffoldDefaults.contentWindowInsets else WindowInsets.navigationBars
    Scaffold(
        contentWindowInsets = insets,
        bottomBar = {
            if (top in topLevelRoutes) {
                NavigationBar {
                    topLevelRoutes.forEach { route ->
                        NavigationBarItem(
                            selected = top == route,
                            onClick = {
                                if (top != route) {
                                    backStack.clear()
                                    backStack.add(route)
                                }
                            },
                            icon = {
                                if (route == Route.Account) {
                                    BadgedBox(badge = {
                                        if (unread.unread.total >
                                            0
                                        ) {
                                            Badge { Text(unread.unread.total.toString()) }
                                        }
                                    }) {
                                        Avatar(user?.avatarUrl, 24.dp)
                                    }
                                } else {
                                    Icon(route.icon(), contentDescription = null)
                                }
                            },
                            label = { Text(route.label()) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavDisplay(
            graph,
            backStack,
            formatters,
            dates,
            prefs,
            user,
            account,
            notifications,
            Modifier.padding(padding),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun AppNavDisplay(
    graph: AppGraph,
    backStack: NavBackStack<NavKey>,
    formatters: Formatters,
    dates: DateFormatters,
    prefs: Preferences,
    user: User?,
    account: AccountKey?,
    notifications: NotificationsViewModel,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val updates = graph.updateRepository
    val update by updates.availableUpdate.collectAsStateWithLifecycle(initialValue = null)
    val updateCheck by updates.checkEnabled.collectAsStateWithLifecycle(initialValue = true)
    // Once per start of the shell; the repository itself asks the release page at most once a day.
    LaunchedEffect(updates) { updates.checkIfDue() }
    val unread =
        notifications.state
            .collectAsStateWithLifecycle()
            .value.unread.total
    // Detail, map and streams of one activity share a view model (and thus the cursor): it lives in
    // the shell's store, not in the per-entry stores the decorators create.
    val sharedOwner = checkNotNull(LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" }
    val detailViewModel: @Composable (String) -> ActivityDetailViewModel = { id ->
        viewModel(viewModelStoreOwner = sharedOwner, key = "activity:$id") {
            ActivityDetailViewModel(
                graph.activityRepository,
                graph.insightsRepository,
                id,
                graph.liveEvents.activityChanges,
            )
        }
    }
    val gearViewModel: @Composable () -> GearViewModel = {
        viewModel(viewModelStoreOwner = sharedOwner, key = "gear") { GearViewModel(graph.gearRepository) }
    }
    val athleteViewModel: @Composable () -> AthleteViewModel = {
        viewModel(viewModelStoreOwner = sharedOwner, key = "athlete") { AthleteViewModel(graph.athleteRepository) }
    }
    val settingsViewModel: @Composable () -> SettingsViewModel = {
        viewModel(viewModelStoreOwner = sharedOwner, key = "settings") { SettingsViewModel(graph.sessionRepository) }
    }
    val back = {
        backStack.removeLastOrNull()
        Unit
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = { key: NavKey ->
            when (key) {
                is Route.Overview -> {
                    NavEntry(key) {
                        val vm =
                            viewModel {
                                OverviewViewModel(
                                    graph.overviewRepository,
                                    graph.syncRepository,
                                    graph.liveEvents.activityChanges,
                                )
                            }
                        OverviewScreen(
                            vm,
                            formatters,
                            dates,
                            federationEnabled = false,
                            OverviewActions(
                                onOpenActivity = { backStack.add(Route.ActivityDetail(it)) },
                                onOpenConnections = { backStack.add(Route.Connections) },
                                onOpenUpdate = { uriHandler.openUri(it.url) },
                                onDismissUpdate = { scope.launch { updates.dismiss(it.version) } },
                            ),
                            update,
                        )
                    }
                }

                is Route.Activities -> {
                    NavEntry(key) {
                        val vm =
                            viewModel {
                                ActivitiesViewModel(
                                    graph.activityRepository,
                                    liveChanges = graph.liveEvents.activityChanges,
                                )
                            }
                        ActivitiesScreen(vm, formatters, dates) { id -> backStack.add(Route.ActivityDetail(id)) }
                    }
                }

                is Route.ActivityDetail -> {
                    NavEntry(key) {
                        val vm = detailViewModel(key.id)
                        ActivityDetailScreen(
                            vm,
                            formatters,
                            dates,
                            prefs.units,
                            ActivityDetailActions(
                                onBack = back,
                                onOpenMap = { backStack.add(Route.ActivityMap(key.id)) },
                                onOpenStreams = { backStack.add(Route.ActivityStreams(key.id)) },
                                onOpenActivity = { backStack.add(Route.ActivityDetail(it)) },
                            ),
                        )
                    }
                }

                is Route.ActivityMap -> {
                    NavEntry(key) {
                        val vm = detailViewModel(key.id)
                        ActivityMapScreen(vm, onBack = { backStack.removeLastOrNull() })
                    }
                }

                is Route.ActivityStreams -> {
                    NavEntry(key) {
                        val vm = detailViewModel(key.id)
                        ActivityStreamsScreen(vm, prefs.units, onBack = { backStack.removeLastOrNull() })
                    }
                }

                is Route.Analysis -> {
                    NavEntry(key) {
                        val vm = viewModel { AnalysisViewModel(graph.analyticsRepository) }
                        AnalysisScreen(
                            vm,
                            dates,
                            onOpenStats = { backStack.add(Route.Stats) },
                            onOpenHeatmap = { backStack.add(Route.Heatmap) },
                        )
                    }
                }

                is Route.Stats -> {
                    NavEntry(key) {
                        val vm = viewModel { StatsViewModel(graph.analyticsRepository) }
                        StatsScreen(vm, formatters, dates, onBack = back) { backStack.add(Route.ActivityDetail(it)) }
                    }
                }

                is Route.Heatmap -> {
                    NavEntry(key) {
                        val vm = viewModel { HeatmapViewModel(graph.analyticsRepository) }
                        HeatmapScreen(vm, onBack = back)
                    }
                }

                is Route.Account -> {
                    NavEntry(key) {
                        val vm =
                            viewModel {
                                AccountViewModel(graph.athleteRepository, graph.gearRepository, graph.recordsRepository)
                            }
                        AccountScreen(
                            vm,
                            user,
                            dates,
                            unread,
                            AccountActions(
                                onNotifications = { backStack.add(Route.Notifications) },
                                onAthlete = { backStack.add(Route.AthleteProfile) },
                                onGear = { backStack.add(Route.Gear) },
                                onRecords = { backStack.add(Route.Records) },
                                onConnections = { backStack.add(Route.Connections) },
                                onSettings = { backStack.add(Route.Settings) },
                                onAbout = { backStack.add(Route.About) },
                                onSignOut = { scope.launch { graph.sessionRepository.signOut() } },
                            ),
                        )
                    }
                }

                is Route.AthleteProfile -> {
                    NavEntry(key) {
                        AthleteProfileScreen(
                            athleteViewModel(),
                            dates,
                            onBack = back,
                        ) { backStack.add(Route.MetricHistory(it)) }
                    }
                }

                is Route.MetricHistory -> {
                    NavEntry(key) { MetricHistoryScreen(athleteViewModel(), key.key, dates, onBack = back) }
                }

                is Route.Gear -> {
                    NavEntry(key) {
                        GearScreen(
                            gearViewModel(),
                            formatters,
                            onBack = back,
                            onOpen = { backStack.add(Route.GearDetail(it)) },
                            onAdd = { backStack.add(Route.GearEdit(null)) },
                        )
                    }
                }

                is Route.GearDetail -> {
                    NavEntry(key) {
                        GearDetailScreen(gearViewModel(), key.id, formatters, dates, onBack = back) {
                            backStack.add(Route.GearEdit(key.id))
                        }
                    }
                }

                is Route.GearEdit -> {
                    NavEntry(key) {
                        val existing = key.id?.let { id -> gearViewModel().state.value.byId(id) }
                        val initial =
                            existing?.let {
                                GearDraft(it.kind, it.name, it.brand, it.model, it.notes, it.purchaseDate)
                            } ?: GearDraft()
                        val vm =
                            viewModel(
                                key = "gear-edit:${key.id}",
                            ) { GearEditViewModel(graph.gearRepository, key.id, initial) }
                        GearEditScreen(vm, isNew = key.id == null, onDone = back)
                    }
                }

                is Route.Records -> {
                    NavEntry(key) {
                        val vm = viewModel { RecordsViewModel(graph.recordsRepository) }
                        RecordsScreen(vm, formatters, dates, onBack = back) { k ->
                            backStack.add(
                                Route.RecordHistory(
                                    k.activityType.wire,
                                    k.metric.name,
                                    k.windowKind.name,
                                    k.windowValue,
                                ),
                            )
                        }
                    }
                }

                is Route.RecordHistory -> {
                    NavEntry(key) {
                        val recordKey =
                            RecordKey(
                                ActivityType.entries.first { it.wire == key.activityType },
                                BestEffortMetric.valueOf(key.metric),
                                BestEffortWindowKind.valueOf(key.windowKind),
                                key.windowValue,
                            )
                        val vm =
                            viewModel(
                                key = "record:$key",
                            ) { RecordHistoryViewModel(graph.recordsRepository, recordKey) }
                        RecordHistoryScreen(
                            vm,
                            formatters,
                            dates,
                            onBack = back,
                        ) { backStack.add(Route.ActivityDetail(it)) }
                    }
                }

                is Route.Notifications -> {
                    NavEntry(key) {
                        NotificationsScreen(notifications, dates, onBack = back) {
                            backStack.add(Route.ActivityDetail(it))
                        }
                    }
                }

                is Route.Connections -> {
                    NavEntry(key) {
                        val vm = viewModel { ConnectionsViewModel(graph.syncRepository) }
                        ConnectionsScreen(vm, dates, onBack = back) { backStack.add(Route.ConnectionDetail(it)) }
                    }
                }

                is Route.ConnectionDetail -> {
                    NavEntry(key) {
                        val vm =
                            viewModel(key = "connection:${key.id}") {
                                ConnectionsViewModel(graph.syncRepository, detailOf = key.id)
                            }
                        ConnectionDetailScreen(vm, key.id, dates, onBack = back) {
                            backStack.add(Route.ActivityDetail(it))
                        }
                    }
                }

                is Route.Settings -> {
                    NavEntry(key) {
                        val updateSetting =
                            UpdateSetting(updateCheck) { on -> scope.launch { updates.setCheckEnabled(on) } }
                        SettingsScreen(
                            settingsViewModel(),
                            user,
                            account,
                            updateSetting.takeIf { updates.supported },
                            onBack = back,
                        ) { backStack.add(Route.ApproveDevice) }
                    }
                }

                is Route.About -> {
                    NavEntry(
                        key,
                    ) { AboutScreen(settingsViewModel(), graph.platform.appVersion, account, onBack = back) }
                }

                is Route.ApproveDevice -> {
                    NavEntry(key) {
                        val vm = viewModel { ApproveDeviceViewModel(graph.deviceApprovalRepository) }
                        ApproveDeviceScreen(vm, onBack = { backStack.removeLastOrNull() })
                    }
                }

                else -> {
                    NavEntry(key) { Text("Unknown route") }
                }
            }
        },
    )
}

@Composable
private fun Route.label(): String =
    when (this) {
        Route.Overview -> stringResource(Res.string.nav_overview)
        Route.Activities -> stringResource(Res.string.nav_activities)
        Route.Analysis -> stringResource(Res.string.nav_analysis)
        Route.Account -> stringResource(Res.string.nav_account)
        else -> ""
    }

private fun Route.icon() =
    when (this) {
        Route.Overview -> {
            Icons.Default.Home
        }

        Route.Activities -> {
            Icons.AutoMirrored.Filled.List
        }

        Route.Analysis -> {
            Icons.Default.Insights
        }

        else -> {
            Icons.AutoMirrored.Filled.List
        }
    }
