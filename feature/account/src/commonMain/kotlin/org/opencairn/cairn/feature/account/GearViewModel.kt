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
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.GearDraft
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class GearUiState(
    val gear: Cached<List<Gear>> = Cached(null, null),
    val busy: Boolean = false,
    val error: String? = null,
    /** Set after a delete so the detail page can leave. */
    val deletedId: String? = null,
) {
    val active: List<Gear> get() = gear.value.orEmpty().filter { !it.retired }
    val retired: List<Gear> get() = gear.value.orEmpty().filter { it.retired }

    fun byId(id: String): Gear? = gear.value?.firstOrNull { it.id == id }
}

/** The gear list and the actions of one item (retire, delete); creating and editing is [GearEditViewModel]. */
class GearViewModel(
    private val repo: GearRepository,
) : ViewModel() {
    private val status = MutableStateFlow(GearUiState())

    val state: StateFlow<GearUiState> =
        combine(repo.observeGear(), status) { g, st -> st.copy(gear = g) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GearUiState())

    init {
        refresh()
    }

    fun refresh() = run { repo.refreshGear() }

    fun setRetired(
        id: String,
        retired: Boolean,
    ) = run { repo.setRetired(id, retired) }

    fun delete(id: String) =
        run {
            repo.deleteGear(id).onSuccess { status.update { it.copy(deletedId = id) } }
        }

    private fun run(block: suspend () -> Result<Unit>) {
        if (status.value.busy) return
        status.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = block().exceptionOrNull()
            status.update { it.copy(busy = false, error = failure?.userMessage()) }
        }
    }
}

data class GearEditUiState(
    val draft: GearDraft = GearDraft(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val valid: Boolean get() =
        draft.name.isNotBlank() &&
            (draft.purchaseDate.isBlank() || ISO_DATE.matches(draft.purchaseDate))

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

/** Create (no [gearId]) or edit one gear item; [GearRepository] refreshes the list after saving. */
class GearEditViewModel(
    private val repo: GearRepository,
    private val gearId: String?,
    initial: GearDraft = GearDraft(),
) : ViewModel() {
    private val _state = MutableStateFlow(GearEditUiState(draft = initial))
    val state: StateFlow<GearEditUiState> = _state

    fun update(transform: (GearDraft) -> GearDraft) =
        _state.update { it.copy(draft = transform(it.draft), error = null) }

    fun save() {
        val st = _state.value
        if (st.saving || !st.valid) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (gearId == null) repo.createGear(st.draft) else repo.updateGear(gearId, st.draft)
            _state.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    error = result.exceptionOrNull()?.userMessage(),
                )
            }
        }
    }
}
