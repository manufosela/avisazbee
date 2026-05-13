package com.manufosela.avisazbee.features.users.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.users.domain.NotificationMode
import com.manufosela.avisazbee.features.users.domain.UpdateNotificationModeUseCase
import com.manufosela.avisazbee.features.users.domain.WatchUserPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationPreferencesUiState(
    val mode: NotificationMode = NotificationMode.BOTH,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
)

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    watchAuthState: WatchAuthStateUseCase,
    watchUserPreferences: WatchUserPreferencesUseCase,
    private val updateMode: UpdateNotificationModeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationPreferencesUiState())
    val state: StateFlow<NotificationPreferencesUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            currentUid
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(null) else watchUserPreferences(uid)
                }
                .collect { preferences ->
                    if (preferences != null) {
                        _state.update {
                            it.copy(mode = preferences.mode, isLoading = false)
                        }
                    }
                }
        }
    }

    fun setMode(mode: NotificationMode) {
        val uid = currentUid.value ?: return
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode, isSaving = true) }
        viewModelScope.launch {
            updateMode(uid, mode)
            _state.update { it.copy(isSaving = false) }
        }
    }
}
