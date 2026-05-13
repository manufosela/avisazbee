package com.manufosela.avisazbee.features.channels.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.CreateChannelUseCase
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

data class CreateChannelUiState(
    val name: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
    private val createChannel: CreateChannelUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateChannelUiState())
    val state: StateFlow<CreateChannelUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    fun onNameChange(newValue: String) {
        _state.update { it.copy(name = newValue, errorMessage = null) }
    }

    fun submit(onCreated: (Channel) -> Unit) {
        val name = _state.value.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(errorMessage = "Pon un nombre al canal.") }
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
                val channel = createChannel(name, uid)
                _state.update { it.copy(isSubmitting = false) }
                onCreated(channel)
            } catch (failure: ChannelFailure) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = createErrorMessage(failure.code),
                    )
                }
            }
        }
    }
}

private fun createErrorMessage(code: String): String = when (code) {
    "invalid_name" -> "El nombre no puede estar vacío."
    else -> "No hemos podido crear el canal."
}
