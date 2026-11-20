package org.opencairn.cairn.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.common.AppError
import org.opencairn.cairn.core.domain.model.Activity
import org.opencairn.cairn.core.domain.model.ActivityChange
import org.opencairn.cairn.core.domain.model.ActivityInsights
import org.opencairn.cairn.core.domain.model.ActivityStream
import org.opencairn.cairn.core.domain.model.Attachment
import org.opencairn.cairn.core.domain.model.BestEffort
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.ExportFile
import org.opencairn.cairn.core.domain.model.ExportFormat
import org.opencairn.cairn.core.domain.model.StreamChannel
import org.opencairn.cairn.core.domain.repository.ActivityInsightsRepository
import org.opencairn.cairn.core.domain.repository.ActivityRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class ActivityDetailUiState(
    val activity: Cached<Activity> = Cached(null, null),
    val bestEfforts: Cached<List<BestEffort>> = Cached(null, null),
    val attachments: Cached<List<Attachment>> = Cached(null, null),
    val stream: Cached<ActivityStream> = Cached(null, null),
    /** Laps, zones, segment efforts, route history, kudos and comments. */
    val insights: ActivityInsights = ActivityInsights(),
    /** Sample index under the chart cursor / map marker; shared by every chart and the map. */
    val selectedIndex: Int? = null,
    /** Channels switched off in the legend; applies to the combined and the stacked charts. */
    val hiddenChannels: Set<StreamChannel> = emptySet(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val exporting: Boolean = false,
    /** A download link that is ready to be opened; the screen opens it once and reports back. */
    val export: ExportFile? = null,
)

class ActivityDetailViewModel(
    private val repo: ActivityRepository,
    private val insightsRepo: ActivityInsightsRepository,
    private val id: String,
    liveChanges: Flow<ActivityChange> = emptyFlow(),
) : ViewModel() {
    private val status = MutableStateFlow(ActivityDetailUiState())
    private val activity = repo.observeActivity(id)
    private val sourceId = activity.map { it.value?.primaryStreamSourceId?.ifBlank { null } }.distinctUntilChanged()
    private val stream =
        sourceId.flatMapLatest { id ->
            if (id == null) flowOf(Cached<ActivityStream>(null, null)) else repo.observeStream(id)
        }
    private val content =
        combine(
            activity,
            repo.observeBestEfforts(id),
            repo.observeAttachments(id),
            stream,
            insightsRepo.observeInsights(id),
        ) { activity, efforts, photos, stream, insights ->
            ActivityDetailUiState(activity, efforts, photos, stream, insights)
        }

    // Collected with the state, so the page follows the server only while it is on screen.
    private val live =
        liveChanges
            .filter { it.activityId == id }
            .onEach { refresh() }
            .map { }
            .onStart { emit(Unit) }

    val state: StateFlow<ActivityDetailUiState> =
        combine(content, status, live) { c, st, _ ->
            st.copy(
                activity = c.activity,
                bestEfforts = c.bestEfforts,
                attachments = c.attachments,
                stream = c.stream,
                insights = c.insights,
            )
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

    fun toggleChannel(channel: StreamChannel) =
        status.update { st ->
            val hidden = if (channel in st.hiddenChannels) st.hiddenChannels - channel else st.hiddenChannels + channel
            st.copy(hiddenChannels = hidden)
        }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val main = repo.refreshActivity(id)
            // Secondary data must not block the page; Unimplemented is expected on older cores.
            val extras =
                listOf(repo.refreshBestEfforts(id), repo.refreshAttachments(id), insightsRepo.refreshInsights(id))
                    .mapNotNull { it.exceptionOrNull() }
                    .filterNot { it is AppError.Unimplemented }
            val failure = main.exceptionOrNull() ?: extras.firstOrNull()
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }

    /** Asks the server for a download link; [ActivityDetailUiState.export] carries it to the screen. */
    fun export(format: ExportFormat) {
        if (status.value.exporting) return
        status.update { it.copy(exporting = true, error = null) }
        viewModelScope.launch {
            val result = insightsRepo.export(id, format)
            status.update {
                it.copy(exporting = false, export = result.getOrNull(), error = result.exceptionOrNull()?.userMessage())
            }
        }
    }

    fun exportOpened() = status.update { it.copy(export = null) }
}
