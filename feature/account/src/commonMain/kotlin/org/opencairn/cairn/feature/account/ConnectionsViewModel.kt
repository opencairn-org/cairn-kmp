package org.opencairn.cairn.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.QueueItem
import org.opencairn.cairn.core.domain.model.SyncAccount
import org.opencairn.cairn.core.domain.model.SyncEvent
import org.opencairn.cairn.core.domain.repository.SyncRepository
import org.opencairn.cairn.ui.design.format.userMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ConnectionsUiState(
    val accounts: Cached<List<SyncAccount>> = Cached(null, null),
    /** Set on the detail page: the account's import history and its open queue items. */
    val history: List<SyncEvent> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    fun account(id: String): SyncAccount? = accounts.value?.firstOrNull { it.id == id }
}

/**
 * The connected accounts and, with a [detailOf] account, its history and queue. Imports move
 * while one watches, so the page asks the server again every [pollEvery] for as long as it is
 * on screen; the polling is part of the state flow and stops with its last collector.
 */
class ConnectionsViewModel(
    private val repo: SyncRepository,
    private val detailOf: String? = null,
    private val pollEvery: Duration = 5.seconds,
) : ViewModel() {
    private val status = MutableStateFlow(ConnectionsUiState())
    private val detail: Flow<Pair<List<SyncEvent>, List<QueueItem>>> =
        if (detailOf == null) {
            MutableStateFlow(emptyList<SyncEvent>() to emptyList())
        } else {
            combine(repo.observeHistory(detailOf), repo.observeQueue(detailOf)) { h, q ->
                h.value.orEmpty() to q.value.orEmpty()
            }
        }

    private val polling =
        flow {
            emit(Unit)
            while (true) {
                reload()
                delay(pollEvery)
            }
        }

    val state: StateFlow<ConnectionsUiState> =
        combine(repo.observeAccounts(), detail, status, polling) { accounts, (history, queue), st, _ ->
            st.copy(accounts = accounts, history = history, queue = queue)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionsUiState())

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        status.update { it.copy(refreshing = true) }
        val results =
            buildList {
                add(repo.refreshAccounts())
                detailOf?.let {
                    add(repo.refreshHistory(it))
                    add(repo.refreshQueue(it))
                }
            }
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
    }
}
