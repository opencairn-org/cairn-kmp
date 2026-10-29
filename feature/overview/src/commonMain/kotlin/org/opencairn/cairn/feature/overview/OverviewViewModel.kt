package org.opencairn.cairn.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.FeedItem
import org.opencairn.cairn.core.domain.model.Overview
import org.opencairn.cairn.core.domain.repository.OverviewRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class OverviewUiState(
    val overview: Cached<Overview> = Cached(null, null),
    val feed: Cached<List<FeedItem>> = Cached(null, null),
    val feedFilter: FeedItem.Source? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val visibleFeed: List<FeedItem> get() =
        feed.value.orEmpty().filter {
            feedFilter == null || it.source == feedFilter
        }
}

class OverviewViewModel(
    private val repo: OverviewRepository,
) : ViewModel() {
    private val status = MutableStateFlow(OverviewUiState())

    val state: StateFlow<OverviewUiState> =
        combine(repo.observeOverview(), repo.observeHomeFeed(), status) { ov, feed, st ->
            st.copy(overview = ov, feed = feed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    init {
        refresh()
    }

    fun setFeedFilter(source: FeedItem.Source?) = status.update { it.copy(feedFilter = source) }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val results = listOf(repo.refreshOverview(), repo.refreshHomeFeed())
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}
