package org.opencairn.cairn.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.DeviceSignIn
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.repository.SessionRepository

/** Server entry, discovery and the sign-in paths: browser, a code approved on another device, personal access token. */
data class LoginUiState(
    val serverInput: String = "",
    val server: Server? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val showTokenEntry: Boolean = false,
    val token: String = "",
    /** Set while this device waits for another one to approve its code. */
    val device: DeviceSignIn? = null,
)

class LoginViewModel(
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state
    private var deviceJob: Job? = null

    fun onServerInput(value: String) = _state.update { it.copy(serverInput = value, error = null) }

    fun onTokenInput(value: String) = _state.update { it.copy(token = value, error = null) }

    fun toggleTokenEntry() = _state.update { it.copy(showTokenEntry = !it.showTokenEntry, error = null) }

    fun changeServer() = _state.update { it.copy(server = null, error = null, showTokenEntry = false, token = "") }

    fun discover() {
        val input = _state.value.serverInput.trim()
        if (input.isEmpty()) return
        launchBusy {
            session
                .discoverServer(input)
                .onSuccess { server -> _state.update { it.copy(server = server) } }
                .onFailure { fail(it) }
        }
    }

    fun signInWithBrowser() {
        val server = _state.value.server ?: return
        launchBusy { session.signIn(server).onSuccess { reset() }.onFailure { fail(it) } }
    }

    fun signInWithToken() {
        val server = _state.value.server ?: return
        val token = _state.value.token.trim()
        if (token.isEmpty()) return
        launchBusy { session.signInWithToken(server, token).onSuccess { reset() }.onFailure { fail(it) } }
    }

    /** Shows a code for another signed-in device to approve, then waits for the approval. */
    fun signInWithCode() {
        val server = _state.value.server ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        deviceJob =
            viewModelScope.launch {
                try {
                    session
                        .startDeviceSignIn(server)
                        .onFailure { fail(it) }
                        .onSuccess { code ->
                            _state.update { it.copy(device = code) }
                            session.completeDeviceSignIn(server, code).onSuccess { reset() }.onFailure { fail(it) }
                        }
                } finally {
                    _state.update { it.copy(busy = false, device = null) }
                }
            }
    }

    fun cancelDeviceSignIn() {
        deviceJob?.cancel()
        deviceJob = null
    }

    /** After a successful sign-in nothing of the entry (in particular the token) stays in memory. */
    private fun reset() = _state.update { LoginUiState() }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun fail(e: Throwable) {
        val message =
            when (e) {
                is AppError.Network, is AppError.Unavailable -> "Could not reach this server."
                is AppError.Unauthenticated -> "The server rejected the credentials."
                is AppError.PermissionDenied -> "This account is not allowed to use the app."
                else -> e.message ?: "Sign-in failed."
            }
        _state.update { it.copy(error = message) }
    }
}
