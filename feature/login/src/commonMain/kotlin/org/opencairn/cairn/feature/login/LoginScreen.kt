package org.opencairn.cairn.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.app_icon
import org.opencairn.cairn.ui.design.resources.app_name
import org.opencairn.cairn.ui.design.resources.login_token_label
import org.opencairn.cairn.ui.design.resources.login_with_browser
import org.opencairn.cairn.ui.design.resources.login_with_code
import org.opencairn.cairn.ui.design.resources.login_with_token
import org.opencairn.cairn.ui.design.resources.server_continue
import org.opencairn.cairn.ui.design.resources.server_title
import org.opencairn.cairn.ui.design.resources.server_url_hint
import org.opencairn.cairn.ui.design.resources.server_url_label
import org.opencairn.cairn.ui.design.resources.server_version

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        actions =
            LoginActions(
                onServerInput = viewModel::onServerInput,
                onDiscover = viewModel::discover,
                onBrowser = viewModel::signInWithBrowser,
                onCode = viewModel::signInWithCode,
                onCancelCode = viewModel::cancelDeviceSignIn,
                onToggleToken = viewModel::toggleTokenEntry,
                onTokenInput = viewModel::onTokenInput,
                onTokenSignIn = viewModel::signInWithToken,
                onChangeServer = viewModel::changeServer,
            ),
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    actions: LoginActions,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo()
            Text(
                stringResource(Res.string.server_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            val server = state.server
            val device = state.device
            when {
                server == null -> ServerStep(state, actions)
                device != null -> DeviceCodeStep(device, actions.onCancelCode)
                else -> SignInStep(server, state, actions)
            }
            if (state.busy && device == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The app icon and name, centred above whichever step is showing. */
@Composable
private fun ColumnScope.Logo() {
    Image(
        painter = painterResource(Res.drawable.app_icon),
        contentDescription = null,
        modifier =
            Modifier
                .size(LOGO_SIZE)
                .clip(RoundedCornerShape(LOGO_SIZE / 5))
                .align(Alignment.CenterHorizontally),
    )
    Text(
        stringResource(Res.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
    )
}

private val LOGO_SIZE = 96.dp

@Composable
private fun ColumnScope.ServerStep(
    state: LoginUiState,
    actions: LoginActions,
) {
    OutlinedTextField(
        value = state.serverInput,
        onValueChange = actions.onServerInput,
        label = { Text(stringResource(Res.string.server_url_label)) },
        placeholder = { Text(stringResource(Res.string.server_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { actions.onDiscover() }),
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    Button(
        onClick = actions.onDiscover,
        enabled = !state.busy && state.serverInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.server_continue))
    }
}

@Composable
private fun ColumnScope.SignInStep(
    server: Server,
    state: LoginUiState,
    actions: LoginActions,
) {
    Text(server.host, style = MaterialTheme.typography.titleMedium)
    server.version?.let {
        Text(stringResource(Res.string.server_version, it), style = MaterialTheme.typography.bodySmall)
    }
    if (!state.showTokenEntry) {
        Button(
            onClick = actions.onBrowser,
            enabled = !state.busy && server.oauthEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.login_with_browser))
        }
        OutlinedButton(
            onClick = actions.onCode,
            enabled = !state.busy && server.oauthEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.login_with_code))
        }
        TextButton(onClick = actions.onToggleToken, enabled = !state.busy) {
            Text(stringResource(Res.string.login_with_token))
        }
    } else {
        TokenEntry(state, actions)
    }
    TextButton(onClick = actions.onChangeServer, enabled = !state.busy) { Text("Change server") }
}

@Composable
private fun ColumnScope.TokenEntry(
    state: LoginUiState,
    actions: LoginActions,
) {
    OutlinedTextField(
        value = state.token,
        onValueChange = actions.onTokenInput,
        label = { Text(stringResource(Res.string.login_token_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { actions.onTokenSignIn() }),
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    Button(
        onClick = actions.onTokenSignIn,
        enabled = !state.busy && state.token.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.server_continue))
    }
    TextButton(onClick = actions.onToggleToken, enabled = !state.busy) {
        Text(stringResource(Res.string.login_with_browser))
    }
}
