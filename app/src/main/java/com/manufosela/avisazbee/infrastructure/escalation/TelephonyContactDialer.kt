@file:Suppress("DEPRECATION")

package com.manufosela.avisazbee.infrastructure.escalation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Implementación real del [ContactDialer] usando la API de telefonía.
 *
 * Heurística de detección (descrita en ARCHITECTURE §3.11):
 *   - si la línea no entra en OFFHOOK durante `perContactRingSeconds`,
 *     se cuelga y se pasa al siguiente contacto;
 *   - si entra en OFFHOOK pero la duración tras la respuesta es < 5 s,
 *     se asume buzón de voz y se pasa al siguiente;
 *   - si la duración es >= 5 s, se considera respuesta humana y la
 *     escalada termina.
 *
 * Permisos requeridos en runtime:
 *   - android.permission.CALL_PHONE
 *   - android.permission.READ_PHONE_STATE
 *   - android.permission.MODIFY_AUDIO_SETTINGS
 *
 * Sin esos permisos `callOnce` devuelve `false` inmediatamente y la
 * cadena pasa al siguiente contacto / da una vuelta en bucle.
 */
class TelephonyContactDialer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactDialer {

    override suspend fun callOnce(
        contact: com.manufosela.avisazbee.features.channels.domain.EscalationContact,
        perContactRingSeconds: Int,
        useSpeakerphone: Boolean,
        isAlertStillPending: suspend () -> Boolean,
    ): Boolean {
        if (!hasRequiredPermissions()) return false
        val telephony = context.getSystemService<TelephonyManager>() ?: return false
        val audio = context.getSystemService<AudioManager>()

        val states = telephonyStates(telephony)

        startCall(contact.phoneE164)
        if (useSpeakerphone) audio?.isSpeakerphoneOn = true

        try {
            val offhook = withTimeoutOrNull(perContactRingSeconds * 1_000L) {
                states.firstWhere { it == TelephonyManager.CALL_STATE_OFFHOOK }
            }
            if (offhook == null) {
                endCall()
                return false
            }
            // Si en los próximos 5 s la línea pasa a IDLE, fue buzón.
            val idleQuickly = withTimeoutOrNull(MIN_HUMAN_PICKUP_MILLIS) {
                states.firstWhere { it == TelephonyManager.CALL_STATE_IDLE }
            }
            if (idleQuickly != null) return false
            // Llamada en curso suficientemente larga: respuesta humana
            // probable. Esperamos hasta que cuelgue para no marcar al
            // siguiente. También se sale si la alerta deja de estar
            // pending (claim/resolve).
            while (isAlertStillPending()) {
                val nextState = withTimeoutOrNull(POLL_ALERT_MILLIS) { states.firstWhere { true } }
                if (nextState == TelephonyManager.CALL_STATE_IDLE) break
            }
            return true
        } catch (cancel: CancellationException) {
            endCall()
            throw cancel
        } finally {
            if (useSpeakerphone) audio?.isSpeakerphoneOn = false
        }
    }

    private fun startCall(phoneE164: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneE164")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun endCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val telecom = context.getSystemService<TelecomManager>() ?: return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { telecom.endCall() }
    }

    private fun hasRequiredPermissions(): Boolean {
        val required = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun telephonyStates(telephony: TelephonyManager): Flow<Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return callStateFlowApi31(telephony)
        }
        @Suppress("DEPRECATION")
        return callStateFlowLegacy(telephony)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun callStateFlowApi31(telephony: TelephonyManager): Flow<Int> = callbackFlow {
        val executor = context.mainExecutor
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                trySend(state)
            }
        }
        telephony.registerTelephonyCallback(executor, callback)
        awaitClose { telephony.unregisterTelephonyCallback(callback) }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun callStateFlowLegacy(telephony: TelephonyManager): Flow<Int> = callbackFlow {
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                trySend(state)
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        awaitClose { telephony.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    private companion object {
        const val MIN_HUMAN_PICKUP_MILLIS = 5_000L
        const val POLL_ALERT_MILLIS = 3_000L
    }
}

private suspend fun <T> Flow<T>.firstWhere(predicate: (T) -> Boolean): T = first { predicate(it) }
