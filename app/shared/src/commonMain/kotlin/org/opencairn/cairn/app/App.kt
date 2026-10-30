package org.opencairn.cairn.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import org.opencairn.cairn.feature.account.GearDetailScreen
import org.opencairn.cairn.feature.account.GearEditScreen
import org.opencairn.cairn.feature.account.GearEditViewModel
import org.opencairn.cairn.feature.account.GearScreen
import org.opencairn.cairn.feature.account.GearViewModel
import org.opencairn.cairn.feature.account.MetricHistoryScreen
import org.opencairn.cairn.feature.account.RecordHistoryScreen
import org.opencairn.cairn.feature.account.RecordHistoryViewModel
import org.opencairn.cairn.feature.account.RecordsScreen
import org.opencairn.cairn.feature.account.RecordsViewModel
import org.opencairn.cairn.feature.account.SettingsScreen
import org.opencairn.cairn.feature.account.SettingsViewModel
import org.opencairn.cairn.feature.activities.ActivitiesScreen
import org.opencairn.cairn.feature.activities.ActivitiesViewModel
import org.opencairn.cairn.feature.activities.ActivityDetailScreen
import org.opencairn.cairn.feature.activities.ActivityDetailViewModel
import org.opencairn.cairn.feature.activities.ActivityMapScreen
import org.opencairn.cairn.feature.activities.ActivityStreamsScreen
import org.opencairn.cairn.feature.login.ApproveDeviceScreen
import org.opencairn.cairn.feature.login.ApproveDeviceViewModel
import org.opencairn.cairn.feature.login.LoginScreen
import org.opencairn.cairn.feature.login.LoginViewModel
import org.opencairn.cairn.feature.overview.OverviewScreen
import org.opencairn.cairn.feature.overview.OverviewViewModel
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.nav_account
import org.opencairn.cairn.ui.design.resources.nav_activities
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
                    subclass(Route.Account::class)
                    subclass(Route.AthleteProfile::class)
                    subclass(Route.MetricHistory::class)
                    subclass(Route.Gear::class)
                    subclass(Route.GearDetail::class)
                    subclass(Route.GearEdit::class)
                    subclass(Route.Records::class)
                    subclass(Route.RecordHistory::class)
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
                                    Avatar(user?.avatarUrl, 24.dp)
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
        AppNavDisplay(graph, backStack, formatters, dates, prefs, user, account, Modifier.padding(padding))
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
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    // Detail, map and streams of one activity share a view model (and thus the cursor): it lives in
    // the shell's store, not in the per-entry stores the decorators create.
    val sharedOwner = checkNotNull(LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" }
    val detailViewModel: @Composable (String) -> ActivityDetailViewModel = { id ->
        viewModel(viewModelStoreOwner = sharedOwner, key = "activity:$id") {
            ActivityDetailViewModel(graph.activityRepository, id)
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
                        val vm = viewModel { OverviewViewModel(graph.overviewRepository) }
                        OverviewScreen(vm, formatters, dates, federationEnabled = false) { id ->
                            backStack.add(Route.ActivityDetail(id))
                        }
                    }
                }

                is Route.Activities -> {
                    NavEntry(key) {
                        val vm = viewModel { ActivitiesViewModel(graph.activityRepository) }
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
                            onBack = { backStack.removeLastOrNull() },
                            onOpenMap = { backStack.add(Route.ActivityMap(key.id)) },
                            onOpenStreams = { backStack.add(Route.ActivityStreams(key.id)) },
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
                            AccountActions(
                                onAthlete = { backStack.add(Route.AthleteProfile) },
                                onGear = { backStack.add(Route.Gear) },
                                onRecords = { backStack.add(Route.Records) },
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

                is Route.Settings -> {
                    NavEntry(key) {
                        SettingsScreen(
                            settingsViewModel(),
                            user,
                            account,
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

        else -> {
            Icons.AutoMirrored.Filled.List
        }
    }
