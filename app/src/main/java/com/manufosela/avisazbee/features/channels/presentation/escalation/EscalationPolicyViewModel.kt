package com.manufosela.avisazbee.features.channels.presentation.escalation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manufosela.avisazbee.app.navigation.Routes
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.EscalationContact
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicy
import com.manufosela.avisazbee.features.channels.domain.UpdateEscalationPolicyUseCase
import com.manufosela.avisazbee.features.channels.domain.WatchEscalationPolicyUseCase
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

data class EscalationFormState(
    val enabled: Boolean = false,
    val escalateAfterSeconds: Int = 180,
    val perContactRingSeconds: Int = 25,
    val useSpeakerphone: Boolean = true,
    val loopUntilAnswered: Boolean = true,
    val contacts: List<EscalationContact> = emptyList(),
)

data class EscalationUiState(
    val form: EscalationFormState = EscalationFormState(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class EscalationPolicyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    watchPolicy: WatchEscalationPolicyUseCase,
    watchAuthState: WatchAuthStateUseCase,
    private val updatePolicy: UpdateEscalationPolicyUseCase,
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID])

    private val _state = MutableStateFlow(EscalationUiState())
    val state: StateFlow<EscalationUiState> = _state.asStateFlow()

    private val currentUid: StateFlow<String?> = watchAuthState()
        .map { it?.uid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            watchPolicy(channelId).collect { policy ->
                _state.update {
                    if (it.isSaving) it else it.copy(form = policy.toFormState(), isLoading = false)
                }
            }
        }
    }

    fun setEnabled(value: Boolean) = updateForm { it.copy(enabled = value) }

    fun setEscalateAfterSeconds(value: Int) =
        updateForm { it.copy(escalateAfterSeconds = value.coerceAtLeast(30)) }

    fun setPerContactRingSeconds(value: Int) =
        updateForm { it.copy(perContactRingSeconds = value.coerceIn(10, 90)) }

    fun setUseSpeakerphone(value: Boolean) = updateForm { it.copy(useSpeakerphone = value) }

    fun setLoopUntilAnswered(value: Boolean) = updateForm { it.copy(loopUntilAnswered = value) }

    fun addContact(name: String, phone: String) = updateForm {
        it.copy(contacts = it.contacts + EscalationContact(name.trim(), phone.trim()))
    }

    fun removeContact(index: Int) = updateForm {
        it.copy(contacts = it.contacts.toMutableList().apply { removeAt(index) })
    }

    fun moveContactUp(index: Int) = updateForm {
        if (index <= 0) it else it.copy(
            contacts = it.contacts.toMutableList().apply {
                add(index - 1, removeAt(index))
            },
        )
    }

    fun moveContactDown(index: Int) = updateForm {
        if (index >= it.contacts.lastIndex) it else it.copy(
            contacts = it.contacts.toMutableList().apply {
                add(index + 1, removeAt(index))
            },
        )
    }

    fun save() {
        val uid = currentUid.value
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Sesión caducada.") }
            return
        }
        val policy = runCatching { _state.value.form.toPolicy() }.getOrElse {
            _state.update { it.copy(errorMessage = it.toRangeMessage()) }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                updatePolicy(channelId, uid, policy)
                _state.update {
                    it.copy(isSaving = false, savedMessage = "Política guardada.")
                }
            } catch (failure: ChannelFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = escalationErrorMessage(failure.code),
                    )
                }
            }
        }
    }

    fun dismissFeedback() {
        _state.update { it.copy(savedMessage = null, errorMessage = null) }
    }

    private fun updateForm(transform: (EscalationFormState) -> EscalationFormState) {
        _state.update { it.copy(form = transform(it.form)) }
    }
}

private fun EscalationPolicy.toFormState(): EscalationFormState = EscalationFormState(
    enabled = enabled,
    escalateAfterSeconds = escalateAfterSeconds,
    perContactRingSeconds = perContactRingSeconds,
    useSpeakerphone = useSpeakerphone,
    loopUntilAnswered = loopUntilAnswered,
    contacts = contacts,
)

private fun EscalationFormState.toPolicy(): EscalationPolicy = EscalationPolicy(
    enabled = enabled,
    escalateAfterSeconds = escalateAfterSeconds,
    contacts = contacts,
    perContactRingSeconds = perContactRingSeconds,
    useSpeakerphone = useSpeakerphone,
    loopUntilAnswered = loopUntilAnswered,
)

private fun EscalationUiState.toRangeMessage(): String =
    "Revisa los segundos: escalada >= 30, timbre entre 10 y 90."

private fun escalationErrorMessage(code: String): String = when (code) {
    "empty_contacts" -> "Si la escalada está activa necesitas al menos un contacto."
    "invalid_phone" -> "Algún teléfono no está en formato E.164 (+34600123456)."
    "invalid_contact_name" -> "Algún contacto no tiene nombre."
    "not_authorized" -> "Sólo el dueño o un admin pueden editar la política."
    "not_found" -> "El canal ya no existe."
    else -> "No hemos podido guardar la política."
}
