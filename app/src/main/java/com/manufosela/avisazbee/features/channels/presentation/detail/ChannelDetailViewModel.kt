package com.manufosela.avisazbee.features.channels.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.app.navigation.Routes
import com.manufosela.avisazbee.features.alerts.domain.Alert
import com.manufosela.avisazbee.features.alerts.domain.AlertFailure
import com.manufosela.avisazbee.features.alerts.domain.ClaimAlertUseCase
import com.manufosela.avisazbee.features.alerts.domain.ResolveAlertUseCase
import com.manufosela.avisazbee.features.alerts.domain.WatchChannelAlertsUseCase
import com.manufosela.avisazbee.features.auth.domain.AppUser
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.WatchChannelMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelDetailUiState(
    val channel: Channel? = null,
    val alerts: List<Alert> = emptyList(),
    val members: List<ChannelMember> = emptyList(),
    val currentUser: AppUser? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channels: ChannelRepository,
    watchAuthState: WatchAuthStateUseCase,
    watchAlerts: WatchChannelAlertsUseCase,
    watchMembers: WatchChannelMembersUseCase,
    private val claimAlert: ClaimAlertUseCase,
    private val resolveAlert: ResolveAlertUseCase,
) : ViewModel() {

    private val channelId: String = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID])

    private val _state = MutableStateFlow(ChannelDetailUiState())
    val state: StateFlow<ChannelDetailUiState> = _state.asStateFlow()

    private val combined = combine(
        watchAlerts(channelId),
        watchMembers(channelId),
        watchAuthState(),
    ) { alerts, members, user -> Triple(alerts, members, user) }

    init {
        viewModelScope.launch {
            val channel = channels.findById(channelId)
            if (channel == null) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            _state.update { it.copy(channel = channel, isLoading = false) }
        }
        combined
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = Triple(emptyList(), emptyList(), null),
            )
            .let { flow ->
                viewModelScope.launch {
                    flow.collect { (alerts, members, user) ->
                        _state.update {
                            it.copy(alerts = alerts, members = members, currentUser = user)
                        }
                    }
                }
            }
    }

    fun claim(alertId: String) {
        val uid = _state.value.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                claimAlert(alertId, uid)
            } catch (failure: AlertFailure) {
                _state.update { it.copy(actionError = alertErrorMessage(failure.code)) }
            }
        }
    }

    fun resolve(alertId: String, resolution: String) {
        val uid = _state.value.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                resolveAlert(alertId, uid, resolution)
            } catch (failure: AlertFailure) {
                _state.update { it.copy(actionError = alertErrorMessage(failure.code)) }
            }
        }
    }

    fun dismissActionError() {
        _state.update { it.copy(actionError = null) }
    }
}

private fun alertErrorMessage(code: String): String = when (code) {
    "already_claimed" -> "Otra persona acaba de aceptar este aviso."
    "already_resolved" -> "Este aviso ya está cerrado."
    "invalid_resolution" -> "Escribe un mensaje al cerrar el aviso."
    "not_found" -> "El aviso ya no existe."
    else -> "No hemos podido completar la acción. Inténtalo de nuevo."
}
