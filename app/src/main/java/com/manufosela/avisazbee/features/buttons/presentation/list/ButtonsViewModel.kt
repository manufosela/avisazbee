package com.manufosela.avisazbee.features.buttons.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.app.navigation.Routes
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.buttons.domain.Button
import com.manufosela.avisazbee.features.buttons.domain.ButtonFailure
import com.manufosela.avisazbee.features.buttons.domain.SetButtonEnabledUseCase
import com.manufosela.avisazbee.features.buttons.domain.WatchChannelButtonsUseCase
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

sealed interface ButtonsUiState {
    data object Loading : ButtonsUiState
    data class Loaded(val channelId: String, val buttons: List<Button>) : ButtonsUiState
}

@HiltViewModel
class ButtonsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    watchChannelButtons: WatchChannelButtonsUseCase,
    watchAuthState: WatchAuthStateUseCase,
    private val setButtonEnabled: SetButtonEnabledUseCase,
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID])

    val state: StateFlow<ButtonsUiState> = watchChannelButtons(channelId)
        .map<List<Button>, ButtonsUiState> { ButtonsUiState.Loaded(channelId, it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ButtonsUiState.Loading,
        )

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun toggleEnabled(buttonId: String, enabled: Boolean) {
        val uid = currentUid.value ?: return
        viewModelScope.launch {
            try {
                setButtonEnabled(buttonId = buttonId, callerUid = uid, enabled = enabled)
            } catch (failure: ButtonFailure) {
                _errorMessage.update { buttonErrorMessage(failure.code) }
            } catch (failure: ChannelFailure) {
                _errorMessage.update { channelErrorMessage(failure.code) }
            }
        }
    }

    fun dismissError() {
        _errorMessage.update { null }
    }
}

private fun buttonErrorMessage(code: String): String = when (code) {
    "not_found" -> "El botón ya no existe."
    else -> "No hemos podido actualizar el botón."
}

private fun channelErrorMessage(code: String): String = when (code) {
    "not_authorized" -> "No tienes permisos para gestionar este botón."
    else -> "No hemos podido actualizar el botón."
}
