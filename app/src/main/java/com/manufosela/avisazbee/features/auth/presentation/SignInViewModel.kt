package com.manufosela.avisazbee.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.AuthFailure
import com.manufosela.avisazbee.features.auth.domain.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val errorCode: String? = null,
    val errorDetail: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInWithGoogle: SignInWithGoogleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun signIn(onSignedIn: () -> Unit) {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, errorCode = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                signInWithGoogle()
                _state.update { it.copy(isSigningIn = false) }
                onSignedIn()
            } catch (failure: AuthFailure) {
                _state.update {
                    it.copy(
                        isSigningIn = false,
                        errorCode = failure.code,
                        errorDetail = failure.message,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorCode = null, errorDetail = null) }
    }
}
