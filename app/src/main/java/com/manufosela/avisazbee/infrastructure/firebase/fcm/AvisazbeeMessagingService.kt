package com.manufosela.avisazbee.infrastructure.firebase.fcm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.manufosela.avisazbee.features.users.domain.NotificationMode
import com.manufosela.avisazbee.features.users.domain.UserPreferencesRepository
import com.manufosela.avisazbee.infrastructure.device.DeviceRegistrar
import com.manufosela.avisazbee.infrastructure.escalation.EscalationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives FCM messages and surfaces them as local notifications.
 *
 * Payload contract from the Cloud Function:
 *   data = {
 *     "alertId":     "<id>",
 *     "channelId":   "<id>",
 *     "channelName": "Casa Manu",
 *     "title":       "Botón pulsado",
 *     "message":     "Pulsación corta en Botón cocina",
 *   }
 */
@AndroidEntryPoint
class AvisazbeeMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifications: AlertNotificationHelper
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var deviceRegistrar: DeviceRegistrar
    @Inject lateinit var userPreferences: UserPreferencesRepository
    @Inject lateinit var escalation: EscalationCoordinator

    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch { deviceRegistrar.registerCurrentDevice(uid) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val alertId = data["alertId"] ?: return
        val channelId = data["channelId"]
        val kind = data["kind"]
        // alertLifecycle envía data {alertId, channelId, kind, actor}.
        // En ese caso solo nos interesa cancelar la escalada local.
        if (kind == "claim" || kind == "resolve") {
            escalation.cancel(alertId)
            return
        }
        val channelName = data["channelName"] ?: "avisazbee"
        val title = data["title"] ?: "Aviso"
        val body = data["message"] ?: ""
        val uid = auth.currentUser?.uid
        scope.launch {
            val mode = uid?.let { runCatching { userPreferences.getFor(it).mode }.getOrNull() }
                ?: NotificationMode.BOTH
            notifications.showAlert(
                alertId = alertId,
                channelName = channelName,
                title = title,
                message = body,
                mode = mode,
            )
            if (channelId != null) {
                runCatching { escalation.schedule(alertId, channelId) }
            }
        }
    }
}
