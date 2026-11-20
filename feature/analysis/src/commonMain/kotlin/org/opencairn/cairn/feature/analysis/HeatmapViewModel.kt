package org.opencairn.cairn.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.ActivityFilter
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.Heatmap
import org.opencairn.cairn.core.domain.repository.AnalyticsRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class HeatmapUiState(
    /** Null shows every sport. */
    val type: ActivityType? = null,
    val heatmap: Heatmap? = null,
    /** The sports to choose from; taken from the unfiltered answer so the chips do not jump. */
    val sports: List<ActivityType> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class HeatmapViewModel(
    private val repo: AnalyticsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HeatmapUiState())
    val state: StateFlow<HeatmapUiState> = _state
    private var loading: Job? = null

    init {
        load()
    }

    fun setType(type: ActivityType?) {
        if (type != _state.value.type) load(type)
    }

    fun load(type: ActivityType? = _state.value.type) {
        // A newer selection replaces a request that is still on its way.
        loading?.cancel()
        _state.update { it.copy(type = type, loading = true, error = null) }
        loading =
            viewModelScope.launch {
                val result = repo.heatmap(ActivityFilter(types = setOfNotNull(type)))
                _state.update { st ->
                    val heatmap = result.getOrNull()
                    st.copy(
                        loading = false,
                        heatmap = heatmap ?: st.heatmap,
                        sports = if (type == null && heatmap != null) heatmap.sports() else st.sports,
                        error = result.exceptionOrNull()?.userMessage(),
                    )
                }
            }
    }

    private fun Heatmap.sports(): List<ActivityType> =
        facets.types
            .sortedByDescending { it.count }
            .map { ActivityType.fromWire(it.value) }
            .filter { it != ActivityType.UNSPECIFIED }
}
