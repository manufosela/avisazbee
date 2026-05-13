package com.manufosela.avisazbee.features.buttons.presentation.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.app.navigation.Routes
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.buttons.domain.ButtonFailure
import com.manufosela.avisazbee.features.buttons.domain.CreateButtonUseCase
import com.manufosela.avisazbee.features.buttons.domain.NewButton
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
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

data class CreateButtonUiState(
    val ieee: String = "",
    val name: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdButton: NewButton? = null,
)

@HiltViewModel
class CreateButtonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    watchAuthState: WatchAuthStateUseCase,
    private val createButton: CreateButtonUseCase,
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID])

    private val _state = MutableStateFlow(CreateButtonUiState())
    val state: StateFlow<CreateButtonUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    fun onIeeeChange(newValue: String) {
        _state.update { it.copy(ieee = newValue, errorMessage = null) }
    }

    fun onNameChange(newValue: String) {
        _state.update { it.copy(name = newValue, errorMessage = null) }
    }

    fun submit() {
        val ieee = _state.value.ieee.trim()
        val name = _state.value.name.trim()
        if (ieee.isEmpty()) {
            _state.update { it.copy(errorMessage = "Introduce la IEEE MAC del botón.") }
            return
        }
        if (name.isEmpty()) {
            _state.update { it.copy(errorMessage = "Pon un nombre al botón.") }
            return
        }
        val uid = currentUid.value
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Sesión caducada.") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val newButton = createButton(
                    rawButtonId = ieee,
                    name = name,
                    channelId = channelId,
                    callerUid = uid,
                )
                _state.update { it.copy(isSubmitting = false, createdButton = newButton) }
            } catch (failure: ButtonFailure) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = buttonErrorMessage(failure.code),
                    )
                }
            } catch (failure: ChannelFailure) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = channelErrorMessage(failure.code),
                    )
                }
            }
        }
    }
}

private fun buttonErrorMessage(code: String): String = when (code) {
    "invalid_button_id" -> "Esa no es una IEEE MAC válida. Debe ser 8 pares hexadecimales."
    "invalid_button_name" -> "El nombre no puede estar vacío."
    "already_exists" -> "Ese botón ya está registrado."
    else -> "No hemos podido registrar el botón."
}

private fun channelErrorMessage(code: String): String = when (code) {
    "not_found" -> "El canal ya no existe."
    "not_authorized" -> "Sólo el dueño o un admin pueden registrar botones."
    else -> "No hemos podido registrar el botón."
}
