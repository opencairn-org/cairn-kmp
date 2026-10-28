package org.opencairn.cairn.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.app.home.HomeScreen
import org.opencairn.cairn.feature.login.LoginScreen
import org.opencairn.cairn.feature.login.LoginViewModel
import org.opencairn.cairn.ui.design.theme.CairnTheme

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Root composable shared by every platform entry point: signed-out → login, else the app. */
@Composable
fun CairnApp(graph: AppGraph) {
    CompositionLocalProvider(LocalAppGraph provides graph) {
        CairnTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val user by graph.sessionRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()
                if (user == null) {
                    val vm = viewModel { LoginViewModel(graph.sessionRepository) }
                    LoginScreen(vm)
                } else {
                    HomeScreen(user = user, onSignOut = { scope.launch { graph.sessionRepository.signOut() } })
                }
            }
        }
    }
}
