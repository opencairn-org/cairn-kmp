package org.opencairn.cairn.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Notification
import org.opencairn.cairn.core.domain.model.UnreadCount
import org.opencairn.cairn.core.domain.repository.NotificationRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class NotificationsUiState(
    val notifications: Cached<List<Notification>> = Cached(null, null),
    val unread: UnreadCount = UnreadCount(),
    val unreadOnly: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val visible: List<Notification>
        get() = notifications.value.orEmpty().filter { !unreadOnly || !it.read }
}

/**
 * The notification list and the unread count behind the bottom bar's badge. While its state is
 * collected it reloads whenever the server announces a new notification ([arrivals]).
 */
class NotificationsViewModel(
    private val repo: NotificationRepository,
    arrivals: Flow<Unit> = emptyFlow(),
) : ViewModel() {
    private val status = MutableStateFlow(NotificationsUiState())
    private val live =
        arrivals
            .conflate()
            .onEach { reload() }
            .onStart { emit(Unit) }

    val state: StateFlow<NotificationsUiState> =
        combine(repo.observeNotifications(), repo.observeUnread(), status, live) { list, unread, st, _ ->
            st.copy(notifications = list, unread = unread.value ?: UnreadCount())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        refresh()
    }

    fun setUnreadOnly(only: Boolean) = status.update { it.copy(unreadOnly = only) }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun markRead(id: String) = change { repo.markRead(id) }

    fun markAllRead() = change { repo.markAllRead() }

    private suspend fun reload() {
        status.update { it.copy(refreshing = true) }
        val failure =
            listOf(repo.refreshNotifications(), repo.refreshUnread())
                .mapNotNull { it.exceptionOrNull() }
                // A server without notifications simply has none.
                .firstOrNull { it !is AppError.Unimplemented }
        status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
    }

    private fun change(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val failure = action().exceptionOrNull()
            status.update { it.copy(error = failure?.userMessage()) }
        }
    }
}
