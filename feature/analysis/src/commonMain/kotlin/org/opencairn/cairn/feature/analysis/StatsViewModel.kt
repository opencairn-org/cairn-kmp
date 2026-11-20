package org.opencairn.cairn.feature.analysis

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
import org.opencairn.cairn.core.domain.model.Stats
import org.opencairn.cairn.core.domain.model.YearStat
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class StatsUiState(
    val stats: Cached<Stats> = Cached(null, null),
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** Newest year first, as the page lists them. */
    val years: List<YearStat> get() =
        stats.value
            ?.byYear
            .orEmpty()
            .sortedByDescending { it.year }

    /** The farthest year: the full width of the year bars. */
    val longestYearMeters: Double get() = years.maxOfOrNull { it.distance.value } ?: 0.0
}

class StatsViewModel(
    private val repo: AnalyticsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(StatsUiState())

    val state: StateFlow<StatsUiState> =
        combine(repo.observeStats(), status) { stats, st -> st.copy(stats = stats) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val failure = repo.refreshStats().exceptionOrNull()
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}
