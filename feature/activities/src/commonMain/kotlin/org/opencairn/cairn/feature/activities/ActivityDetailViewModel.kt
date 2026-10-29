package org.opencairn.cairn.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class ActivityDetailUiState(
    val activity: Cached<Activity> = Cached(null, null),
    val bestEfforts: Cached<List<BestEffort>> = Cached(null, null),
    val attachments: Cached<List<Attachment>> = Cached(null, null),
    val stream: Cached<ActivityStream> = Cached(null, null),
    /** Sample index under the chart cursor / map marker; shared by every chart and the map. */
    val selectedIndex: Int? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class ActivityDetailViewModel(
    private val repo: ActivityRepository,
    private val id: String,
) : ViewModel() {
    private val status = MutableStateFlow(ActivityDetailUiState())
    private val activity = repo.observeActivity(id)
    private val sourceId = activity.map { it.value?.primaryStreamSourceId?.ifBlank { null } }.distinctUntilChanged()
    private val stream =
        sourceId.flatMapLatest { id ->
            if (id == null) flowOf(Cached<ActivityStream>(null, null)) else repo.observeStream(id)
        }

    val state: StateFlow<ActivityDetailUiState> =
        combine(
            activity,
            repo.observeBestEfforts(id),
            repo.observeAttachments(id),
            stream,
            status,
        ) { activity, efforts, photos, stream, st ->
            st.copy(activity = activity, bestEfforts = efforts, attachments = photos, stream = stream)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityDetailUiState())

    init {
        refresh()
        // The stream is fetched once the activity row says which source holds it and the cache has none.
        viewModelScope.launch {
            combine(sourceId, stream) { id, s -> id to s.isEmpty }
                .distinctUntilChanged()
                .collect { (id, missing) -> if (id != null && missing) repo.refreshStream(id) }
        }
    }

    fun select(index: Int?) = status.update { it.copy(selectedIndex = index) }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val main = repo.refreshActivity(id)
            // Secondary data must not block the page; Unimplemented is expected on older cores.
            val extras =
                listOf(repo.refreshBestEfforts(id), repo.refreshAttachments(id))
                    .mapNotNull { it.exceptionOrNull() }
                    .filterNot { it is AppError.Unimplemented }
            val failure = main.exceptionOrNull() ?: extras.firstOrNull()
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}
