package org.opencairn.cairn.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.Preferences
import org.opencairn.cairn.core.domain.model.Server
import org.opencairn.cairn.core.domain.repository.SessionRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class SettingsUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val server: Server? = null,
)

/** Format and unit preferences are saved on the server; the current user flow then carries them everywhere. */
class SettingsViewModel(
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(server = session.activeServer()) } }
    }

    fun save(preferences: Preferences) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val failure = session.updatePreferences(preferences).exceptionOrNull()
            _state.update { it.copy(saving = false, error = failure?.userMessage()) }
        }
    }
}
