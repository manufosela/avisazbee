package com.manufosela.avisazbee.app.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

enum class SplashState { Loading, Authenticated, Unauthenticated }

@HiltViewModel
class SplashViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    init {
        watchAuthState()
            .onEach { user ->
                _state.value = if (user == null) {
                    SplashState.Unauthenticated
                } else {
                    SplashState.Authenticated
                }
            }
            .launchIn(viewModelScope)
    }
}
