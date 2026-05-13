package com.manufosela.avisazbee.features.channels.presentation.dialers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.app.navigation.Routes
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.AddDialerUseCase
import com.manufosela.avisazbee.features.channels.domain.ChannelDialer
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.RemoveDialerUseCase
import com.manufosela.avisazbee.features.channels.domain.WatchDialersUseCase
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

data class DialersUiState(
    val dialers: List<ChannelDialer> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class DialersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    watchDialers: WatchDialersUseCase,
    watchAuthState: WatchAuthStateUseCase,
    private val addDialer: AddDialerUseCase,
    private val removeDialer: RemoveDialerUseCase,
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID])

    private val _state = MutableStateFlow(DialersUiState())
    val state: StateFlow<DialersUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            watchDialers(channelId).collect { list ->
                _state.update { it.copy(dialers = list, isLoading = false) }
            }
        }
    }

    fun add(deviceId: String) {
        val uid = currentUid.value ?: return
        viewModelScope.launch {
            try {
                addDialer(channelId, uid, deviceId)
            } catch (failure: ChannelFailure) {
                _state.update { it.copy(errorMessage = dialerErrorMessage(failure.code)) }
            }
        }
    }

    fun remove(deviceId: String) {
        val uid = currentUid.value ?: return
        viewModelScope.launch {
            try {
                removeDialer(channelId, uid, deviceId)
            } catch (failure: ChannelFailure) {
                _state.update { it.copy(errorMessage = dialerErrorMessage(failure.code)) }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

private fun dialerErrorMessage(code: String): String = when (code) {
    "not_authorized" -> "Sólo el dueño o un admin pueden gestionar dialers."
    "not_found" -> "El canal o el dispositivo ya no existen."
    else -> "No hemos podido completar la acción."
}
