package org.opencairn.cairn.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.PendingDevice
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.action_back
import org.opencairn.cairn.ui.design.resources.approve_device_approve
import org.opencairn.cairn.ui.design.resources.approve_device_code_label
import org.opencairn.cairn.ui.design.resources.approve_device_deny
import org.opencairn.cairn.ui.design.resources.approve_device_done_approved
import org.opencairn.cairn.ui.design.resources.approve_device_done_denied
import org.opencairn.cairn.ui.design.resources.approve_device_lookup
import org.opencairn.cairn.ui.design.resources.approve_device_title
import org.opencairn.cairn.ui.design.resources.approve_device_wants
import org.opencairn.cairn.ui.design.resources.approve_device_warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveDeviceScreen(
    viewModel: ApproveDeviceViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.approve_device_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.decided) {
                    true -> {
                        Done(stringResource(Res.string.approve_device_done_approved), viewModel::reset)
                    }

                    false -> {
                        Done(stringResource(Res.string.approve_device_done_denied), viewModel::reset)
                    }

                    null -> {
                        CodeEntry(state, viewModel)
                        state.pending?.let { Consent(it, state.busy, viewModel::decide) }
                    }
                }
                if (state.busy) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CodeEntry(
    state: ApproveDeviceUiState,
    viewModel: ApproveDeviceViewModel,
) {
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeInput,
        label = { Text(stringResource(Res.string.approve_device_code_label)) },
        placeholder = { Text("0000-0000") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.lookup() }),
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    if (state.pending == null) {
        Button(
            onClick = viewModel::lookup,
            enabled = !state.busy && state.codeComplete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.approve_device_lookup))
        }
    }
}

@Composable
private fun Consent(
    pending: PendingDevice,
    busy: Boolean,
    onDecide: (Boolean) -> Unit,
) {
    Text(
        stringResource(Res.string.approve_device_wants, pending.clientName),
        style = MaterialTheme.typography.bodyLarge,
    )
    pending.scopes.forEach { scope ->
        Column {
            Text(scope.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                scope.scope,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color =
                    if (scope.write) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        stringResource(Res.string.approve_device_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { onDecide(false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.approve_device_deny))
        }
        Button(onClick = { onDecide(true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.approve_device_approve))
        }
    }
}

@Composable
private fun Done(
    message: String,
    onAgain: () -> Unit,
) {
    Text(message, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.approve_device_title))
    }
}
