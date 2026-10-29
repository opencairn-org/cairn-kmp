package org.opencairn.cairn.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencairn.cairn.core.domain.model.PendingDevice
import org.opencairn.cairn.core.domain.repository.DeviceApprovalRepository
import org.opencairn.cairn.ui.design.format.userMessage

/** The approving side of a device sign-in: enter the code, see who asks, approve or deny. */
data class ApproveDeviceUiState(
    val code: String = "",
    val pending: PendingDevice? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** True = approved, false = denied, null = undecided. */
    val decided: Boolean? = null,
) {
    val codeComplete: Boolean get() = code.count { it.isDigit() } == USER_CODE_DIGITS
}

class ApproveDeviceViewModel(
    private val repo: DeviceApprovalRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ApproveDeviceUiState())
    val state: StateFlow<ApproveDeviceUiState> = _state

    /** Digits only, shown as 1234-5678. */
    fun onCodeInput(value: String) {
        val digits = value.filter { it.isDigit() }.take(USER_CODE_DIGITS)
        val shown = if (digits.length > HALF) digits.substring(0, HALF) + "-" + digits.substring(HALF) else digits
        _state.update { it.copy(code = shown, pending = null, error = null, decided = null) }
    }

    fun lookup() {
        if (!_state.value.codeComplete) return
        run { repo.lookup(_state.value.code).onSuccess { p -> _state.update { it.copy(pending = p) } } }
    }

    fun decide(approve: Boolean) {
        if (_state.value.pending == null) return
        run {
            repo
                .decide(_state.value.code, approve)
                .onSuccess { _state.update { it.copy(decided = approve, pending = null) } }
        }
    }

    fun reset() = _state.update { ApproveDeviceUiState() }

    private fun run(block: suspend () -> Result<*>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block().onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

private const val USER_CODE_DIGITS = 8
private const val HALF = 4
