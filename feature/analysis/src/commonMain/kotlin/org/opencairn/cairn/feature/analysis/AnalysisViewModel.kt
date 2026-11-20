package org.opencairn.cairn.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.TrainingLoad
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.core.domain.model.TrainingLoadRange
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class AnalysisUiState(
    val range: TrainingLoadRange = TrainingLoadRange.SIX_MONTHS,
    val load: Cached<TrainingLoad> = Cached(null, null),
    /** The day under the chart's crosshair. */
    val selected: Int? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** The day the tiles and the readout describe: the one under the crosshair, else the latest. */
    val shownDay: TrainingLoadDay?
        get() =
            load.value?.days?.let { days ->
                selected?.let(days::getOrNull) ?: days.lastOrNull()
            }

    /** A server that has computed nothing yet answers with an empty series. */
    val hasData: Boolean get() = (load.value?.days?.size ?: 0) >= 2
}

class AnalysisViewModel(
    private val repo: AnalyticsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(AnalysisUiState())

    // The series travels with the range it belongs to: right after a switch the old one must not show.
    private val load =
        status
            .map { it.range }
            .distinctUntilChanged()
            .flatMapLatest { range -> repo.observeTrainingLoad(range).map { range to it } }

    val state: StateFlow<AnalysisUiState> =
        combine(load, status) { (range, load), st ->
            st.copy(load = if (range == st.range) load else Cached(null, null))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    init {
        refresh()
    }

    fun setRange(range: TrainingLoadRange) {
        if (range == status.value.range) return
        // One step, so no frame shows the new range as "nothing there". The crosshair belongs to the old series.
        status.update { it.copy(range = range, selected = null, refreshing = true, error = null) }
        fetch(range)
    }

    fun select(index: Int?) = status.update { it.copy(selected = index) }

    fun refresh() {
        status.update { it.copy(refreshing = true, error = null) }
        fetch(status.value.range)
    }

    private fun fetch(range: TrainingLoadRange) {
        viewModelScope.launch {
            val failure = repo.refreshTrainingLoad(range).exceptionOrNull()
            // A slower answer for a range the user has left must not report into the new one.
            status.update { if (it.range == range) it.copy(refreshing = false, error = failure?.userMessage()) else it }
        }
    }
}
