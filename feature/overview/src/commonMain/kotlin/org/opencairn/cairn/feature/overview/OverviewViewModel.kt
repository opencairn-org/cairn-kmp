package org.opencairn.cairn.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.model.SyncNotice
import org.opencairn.cairn.core.domain.model.syncNotice
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.core.domain.repository.SyncRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class OverviewUiState(
    val overview: Cached<Overview> = Cached(null, null),
    val feed: Cached<List<FeedItem>> = Cached(null, null),
    val feedFilter: FeedItem.Source? = null,
    /** Why a recent activity may not be here yet; null when syncing has nothing to tell. */
    val syncNotice: SyncNotice? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val visibleFeed: List<FeedItem> get() =
        feed.value.orEmpty().filter {
            feedFilter == null || it.source == feedFilter
        }
}

/**
 * Totals, the weeks and the feed, plus a line about syncing when there is something to say.
 * While the page is on screen it reloads whenever the server reports a change ([liveChanges]).
 */
class OverviewViewModel(
    private val repo: OverviewRepository,
    private val sync: SyncRepository,
    liveChanges: Flow<ActivityChange> = emptyFlow(),
) : ViewModel() {
    private val status = MutableStateFlow(OverviewUiState())

    // A burst of imports collapses into the reload that is running and one more after it.
    private val live =
        liveChanges
            .conflate()
            .onEach { reload() }
            .onStart { emit(ActivityChange(ActivityChange.Kind.UPDATED, null)) }

    val state: StateFlow<OverviewUiState> =
        combine(
            repo.observeOverview(),
            repo.observeHomeFeed(),
            sync.observeAccounts(),
            status,
            live,
        ) { ov, feed, accounts, st, _ ->
            st.copy(overview = ov, feed = feed, syncNotice = accounts.value?.syncNotice())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    init {
        refresh()
    }

    fun setFeedFilter(source: FeedItem.Source?) = status.update { it.copy(feedFilter = source) }

    fun refresh() {
        if (status.value.refreshing) return
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        status.update { it.copy(refreshing = true, error = null) }
        val failure =
            listOf(
                repo.refreshOverview(),
                repo.refreshHomeFeed(),
            ).firstNotNullOfOrNull { it.exceptionOrNull() }
        // The sync line is an extra: a server or sign-in without access to connections shows none.
        sync.refreshAccounts()
        status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
    }
}
