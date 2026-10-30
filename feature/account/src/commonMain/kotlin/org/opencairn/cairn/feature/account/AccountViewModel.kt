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
import org.opencairn.cairn.core.domain.model.AthleteMetricEntry
import org.opencairn.cairn.core.domain.model.AthleteMetricSpec
import org.opencairn.cairn.core.domain.model.AthleteProfile
import org.opencairn.cairn.core.domain.model.Cached
import org.opencairn.cairn.core.domain.model.Gear
import org.opencairn.cairn.core.domain.model.PersonalRecord
import org.opencairn.cairn.core.domain.repository.AthleteRepository
import org.opencairn.cairn.core.domain.repository.GearRepository
import org.opencairn.cairn.core.domain.repository.RecordsRepository
import org.opencairn.cairn.ui.design.format.userMessage

data class AccountUiState(
    val profile: Cached<AthleteProfile> = Cached(null, null),
    val gear: Cached<List<Gear>> = Cached(null, null),
    val records: Cached<List<PersonalRecord>> = Cached(null, null),
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** The highlighted parameters with their newest value, in the profile's display order. */
    val highlights: List<Pair<AthleteMetricSpec, AthleteMetricEntry?>>
        get() {
            val p = profile.value ?: return emptyList()
            return AthleteProfile.HIGHLIGHTED.mapNotNull { key -> p.spec(key)?.let { it to p.latest(key) } }
        }
    val activeGearCount: Int get() = gear.value.orEmpty().count { !it.retired }
    val recordCount: Int get() = records.value.orEmpty().size
}

/** The account landing page: a glance at profile, gear and records; each section opens its own page. */
class AccountViewModel(
    private val athlete: AthleteRepository,
    private val gear: GearRepository,
    private val records: RecordsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(AccountUiState())

    val state: StateFlow<AccountUiState> =
        combine(athlete.observeProfile(), gear.observeGear(), records.observeRecords(), status) { p, g, r, st ->
            st.copy(profile = p, gear = g, records = r)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (status.value.refreshing) return
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val results = listOf(athlete.refreshProfile(), gear.refreshGear(), records.refreshRecords())
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            status.update { it.copy(refreshing = false, error = failure?.userMessage()) }
        }
    }
}
