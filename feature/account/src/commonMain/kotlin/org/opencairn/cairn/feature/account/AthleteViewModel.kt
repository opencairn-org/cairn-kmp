package org.opencairn.cairn.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class AthleteUiState(
    val profile: Cached<AthleteProfile> = Cached(null, null),
    val busy: Boolean = false,
    val error: String? = null,
)

/** All parameters with their newest value, and per parameter the history with add and delete. */
class AthleteViewModel(
    private val repo: AthleteRepository,
) : ViewModel() {
    private val status = MutableStateFlow(AthleteUiState())

    val state: StateFlow<AthleteUiState> =
        combine(repo.observeProfile(), status) { p, st -> st.copy(profile = p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AthleteUiState())

    init {
        refresh()
    }

    fun refresh() = run { repo.refreshProfile() }

    fun add(
        key: String,
        date: String,
        value: Double,
    ) = run { repo.addEntry(key, date, value) }

    fun delete(id: String) = run { repo.deleteEntry(id) }

    private fun run(block: suspend () -> Result<Unit>) {
        if (status.value.busy) return
        status.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = block().exceptionOrNull()
            status.update { it.copy(busy = false, error = failure?.userMessage()) }
        }
    }
}
