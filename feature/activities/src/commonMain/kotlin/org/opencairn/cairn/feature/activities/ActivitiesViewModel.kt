package org.opencairn.cairn.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivitySearchPage
import org.opencairn.cairn.core.domain.model.ActivitySort
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class ActivitiesUiState(
    val filter: ActivityFilter = ActivityFilter(),
    val sort: ActivitySort = ActivitySort.DATE_DESC,
    val page: ActivitySearchPage? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val showFilters: Boolean = false,
)

class ActivitiesViewModel(
    private val repo: ActivityRepository,
    initialFilter: ActivityFilter = ActivityFilter(),
    liveChanges: Flow<ActivityChange> = emptyFlow(),
) : ViewModel() {
    private val query = MutableStateFlow(initialFilter to ActivitySort.DATE_DESC)
    private val status = MutableStateFlow(ActivitiesUiState(filter = initialFilter))

    /** Reloading replaces the list with its first page; that must not happen under a reader further down. */
    private var pagedBeyondFirst = false

    // While the list is on screen, activities that arrive, change or go show up on their own.
    private val live =
        liveChanges
            .filter { it.kind != ActivityChange.Kind.RECORD && !pagedBeyondFirst }
            .conflate()
            .onEach { refresh() }
            .map { }
            .onStart { emit(Unit) }

    val state: StateFlow<ActivitiesUiState> =
        combine(
            query.flatMapLatest { (f, s) -> repo.observeSearch(f, s) },
            status,
            live,
        ) { page, st, _ -> st.copy(page = page) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState(filter = initialFilter))

    init {
        refresh()
    }

    fun setFilter(filter: ActivityFilter) = setQuery(filter, query.value.second)

    fun setSort(sort: ActivitySort) = setQuery(query.value.first, sort)

    fun clearFilter() = setQuery(ActivityFilter(), ActivitySort.DATE_DESC)

    fun toggleFilters() = status.update { it.copy(showFilters = !it.showFilters) }

    private fun setQuery(
        filter: ActivityFilter,
        sort: ActivitySort,
    ) {
        query.value = filter to sort
        pagedBeyondFirst = false
        status.update { it.copy(filter = filter, sort = sort, error = null) }
        refresh()
    }

    fun refresh() {
        if (status.value.refreshing) return
        val (f, s) = query.value
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val result = repo.refreshSearch(f, s)
            status.update { it.copy(refreshing = false, error = result.exceptionOrNull()?.userMessage()) }
        }
    }

    fun loadMore() {
        val st = status.value
        if (st.loadingMore || st.refreshing || state.value.page?.hasMore != true) return
        val (f, s) = query.value
        status.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = repo.loadMoreSearch(f, s)
            if (result.isSuccess) pagedBeyondFirst = true
            status.update { it.copy(loadingMore = false, error = result.exceptionOrNull()?.userMessage()) }
        }
    }
}
