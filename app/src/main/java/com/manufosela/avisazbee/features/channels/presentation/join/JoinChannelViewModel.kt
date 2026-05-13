package com.manufosela.avisazbee.features.channels.presentation.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.JoinChannelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinChannelUiState(
    val code: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class JoinChannelViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
    private val joinChannel: JoinChannelUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinChannelUiState())
    val state: StateFlow<JoinChannelUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    fun onCodeChange(newValue: String) {
        _state.update { it.copy(code = newValue.trim().uppercase(), errorMessage = null) }
    }

    fun submit(onJoined: (Channel) -> Unit) {
        val code = _state.value.code
        if (code.isEmpty()) {
            _state.update { it.copy(errorMessage = "Introduce el código de invitación.") }
            return
        }
        val uid = currentUid.value
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Sesión caducada. Vuelve a entrar.") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val channel = joinChannel(uid, code)
                _state.update { it.copy(isSubmitting = false) }
                onJoined(channel)
            } catch (failure: ChannelFailure) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = joinErrorMessage(failure.code),
                    )
                }
            }
        }
    }
}

private fun joinErrorMessage(code: String): String = when (code) {
    "invalid_code" -> "Ese código no existe."
    "invites_disabled" -> "Las invitaciones a ese canal están desactivadas."
    "is_owner" -> "Ya eres el dueño de ese canal."
    "already_member" -> "Ya formas parte de ese canal."
    else -> "No hemos podido unirte al canal."
}
