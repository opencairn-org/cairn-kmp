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
import org.opencairn.cairn.core.domain.model.ActivityType
import org.opencairn.cairn.core.domain.model.BestEffortHistory
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.model.RecordKey
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class RecordsUiState(
    val records: Cached<List<PersonalRecord>> = Cached(null, null),
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** Grouped by sport in the web's order (ride, run, swim, walk, hike, workout, then the rest). */
    val groups: List<Pair<ActivityType, List<PersonalRecord>>>
        get() =
            records.value
                .orEmpty()
                .groupBy { it.activityType }
                .toList()
                .sortedBy { (type, _) -> SPORT_ORDER.indexOf(type).let { if (it < 0) SPORT_ORDER.size else it } }
                .map { (type, list) ->
                    type to
                        list.sortedWith(compareBy({ it.metric.ordinal }, { it.windowKind.ordinal }, { it.windowValue }))
                }

    private companion object {
        val SPORT_ORDER =
            listOf(
                ActivityType.RIDE,
                ActivityType.RUN,
                ActivityType.SWIM,
                ActivityType.WALK,
                ActivityType.HIKE,
                ActivityType.WORKOUT,
            )
    }
}

class RecordsViewModel(
    private val repo: RecordsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(RecordsUiState())

    val state: StateFlow<RecordsUiState> =
        combine(repo.observeRecords(), status) { r, st -> st.copy(records = r) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val failure = repo.refreshRecords().exceptionOrNull()
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}

data class RecordHistoryUiState(
    val history: Cached<BestEffortHistory> = Cached(null, null),
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** The progression of one record's bucket across all activities. */
class RecordHistoryViewModel(
    private val repo: RecordsRepository,
    val key: RecordKey,
) : ViewModel() {
    private val status = MutableStateFlow(RecordHistoryUiState())

    val state: StateFlow<RecordHistoryUiState> =
        combine(repo.observeHistory(key), status) { h, st -> st.copy(history = h) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordHistoryUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val failure = repo.refreshHistory(key).exceptionOrNull()
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}
