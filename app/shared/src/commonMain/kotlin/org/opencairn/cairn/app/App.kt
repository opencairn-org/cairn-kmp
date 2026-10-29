package org.opencairn.cairn.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import org.opencairn.cairn.app.settings.SettingsScreen
import org.opencairn.cairn.core.domain.model.AccountKey
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.User
import org.opencairn.cairn.feature.activities.ActivitiesScreen
import org.opencairn.cairn.feature.activities.ActivitiesViewModel
import org.opencairn.cairn.feature.activities.ActivityDetailScreen
import org.opencairn.cairn.feature.activities.ActivityDetailViewModel
import org.opencairn.cairn.feature.activities.ActivityMapScreen
import org.opencairn.cairn.feature.activities.ActivityStreamsScreen
import org.opencairn.cairn.feature.login.LoginScreen
import org.opencairn.cairn.feature.login.LoginViewModel
import org.opencairn.cairn.feature.overview.OverviewScreen
import org.opencairn.cairn.feature.overview.OverviewViewModel
import org.opencairn.cairn.ui.design.format.DateFormatters
import org.opencairn.cairn.ui.design.format.Formatters
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.nav_activities
import org.opencairn.cairn.ui.design.resources.nav_overview
import org.opencairn.cairn.ui.design.resources.nav_settings
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
                    subclass(Route.Settings::class)
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
    Scaffold(
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
                            icon = { Icon(route.icon(), contentDescription = null) },
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
                            onOpenMap = { backStack.add(Route.ActivityMap(key.id)) },
                            onOpenStreams = { backStack.add(Route.ActivityStreams(key.id)) },
                        )
                    }
                }

                is Route.ActivityMap -> {
                    NavEntry(key) {
                        val vm = detailViewModel(key.id)
                        ActivityMapScreen(vm)
                    }
                }

                is Route.ActivityStreams -> {
                    NavEntry(key) {
                        val vm = detailViewModel(key.id)
                        ActivityStreamsScreen(vm, prefs.units)
                    }
                }

                is Route.Settings -> {
                    NavEntry(key) {
                        SettingsScreen(user, account, graph.platform.appVersion) {
                            scope.launch { graph.sessionRepository.signOut() }
                        }
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
        Route.Settings -> stringResource(Res.string.nav_settings)
        is Route.ActivityDetail, is Route.ActivityMap, is Route.ActivityStreams -> ""
    }

private fun Route.icon() =
    when (this) {
        Route.Overview -> Icons.Default.Home
        Route.Activities -> Icons.AutoMirrored.Filled.List
        Route.Settings -> Icons.Default.Settings
        is Route.ActivityDetail, is Route.ActivityMap, is Route.ActivityStreams -> Icons.AutoMirrored.Filled.List
    }
