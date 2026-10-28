package org.opencairn.cairn.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.repository.SessionRepository

/** Server entry, discovery and the two sign-in paths (browser, personal access token). */
data class LoginUiState(
    val serverInput: String = "",
    val server: Server? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val showTokenEntry: Boolean = false,
    val token: String = "",
)

class LoginViewModel(
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

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
        launchBusy { session.signIn(server).onFailure { fail(it) } }
    }

    fun signInWithToken() {
        val server = _state.value.server ?: return
        val token = _state.value.token.trim()
        if (token.isEmpty()) return
        launchBusy { session.signInWithToken(server, token).onFailure { fail(it) } }
    }

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
