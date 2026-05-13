package com.manufosela.avisazbee.features.channels.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.AppUser
import com.manufosela.avisazbee.features.auth.domain.SignOutUseCase
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.WatchUserChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data object Unauthenticated : ChannelsUiState
    data class Loaded(val channels: List<Channel>) : ChannelsUiState
}

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
    watchUserChannels: WatchUserChannelsUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChannelsUiState> = watchAuthState()
        .flatMapLatest { user: AppUser? ->
            if (user == null) {
                flowOf(ChannelsUiState.Unauthenticated)
            } else {
                watchUserChannels(user.uid).map { ChannelsUiState.Loaded(it) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ChannelsUiState.Loading,
        )

    fun signOut() {
        viewModelScope.launch { signOut.invoke() }
    }
}
