package com.manufosela.avisazbee.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AuthSession { Unknown, SignedIn, SignedOut }

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
) : ViewModel() {

    val session: StateFlow<AuthSession> = watchAuthState()
        .map { user -> if (user == null) AuthSession.SignedOut else AuthSession.SignedIn }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AuthSession.Unknown,
        )
}
