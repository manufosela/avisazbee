package com.manufosela.avisazbee.infrastructure.escalation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.manufosela.avisazbee.R
import com.manufosela.avisazbee.features.alerts.domain.AlertRepository
import com.manufosela.avisazbee.features.alerts.domain.AlertStatus
import com.manufosela.avisazbee.features.channels.domain.EscalationContact
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

/**
 * Worker que dispara la cadena de llamadas a los contactos de un canal
 * cuando una alerta queda sin reclamar tras `escalateAfterSeconds`.
 *
 * Se cancela vía `WorkManager.cancelUniqueWork(...)`; el dispatcher
 * (FCM service) llama a cancel cuando llega una push silenciosa de
 * tipo claim/resolve.
 */
@HiltWorker
class EscalationWork @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val policyRepository: EscalationPolicyRepository,
    private val alertRepository: AlertRepository,
    private val contactDialer: ContactDialer,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("avisazbee")
            .setContentText("Preparando llamadas de escalada…")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val alertId = inputData.getString(KEY_ALERT_ID) ?: return Result.failure()
        val channelId = inputData.getString(KEY_CHANNEL_ID) ?: return Result.failure()
        val policy = runCatching { policyRepository.getFor(channelId) }.getOrNull() ?: return Result.success()
        if (!policy.enabled || policy.contacts.isEmpty()) return Result.success()

        setForeground(getForegroundInfo())
        delay(policy.escalateAfterSeconds * 1_000L)
        if (!stillPending(alertId)) return Result.success()

        loop@ while (!isStopped) {
            for (contact in policy.contacts) {
                if (isStopped) break@loop
                if (!stillPending(alertId)) return Result.success()
                val answered = contactDialer.callOnce(
                    contact = contact,
                    perContactRingSeconds = policy.perContactRingSeconds,
                    useSpeakerphone = policy.useSpeakerphone,
                    isAlertStillPending = { stillPending(alertId) },
                )
                if (answered) return Result.success()
            }
            if (!policy.loopUntilAnswered) break
        }
        return Result.success()
    }

    private suspend fun stillPending(alertId: String): Boolean {
        val alert = runCatching { alertRepository.findById(alertId) }.getOrNull()
        return alert != null && alert.status == AlertStatus.PENDING
    }

    companion object {
        const val KEY_ALERT_ID = "alertId"
        const val KEY_CHANNEL_ID = "channelId"
        private const val NOTIFICATION_ID = 2138
        private const val NOTIFICATION_CHANNEL_ID = "avisazbee_escalation"

        fun ensureNotificationChannel(context: Context) {
            val nm = context.getSystemService<NotificationManager>() ?: return
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Escalada de llamadas",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notificación persistente mientras la app llama a los contactos."
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}

/**
 * Encapsula la lógica de marcar a un contacto. Se inyecta para poder
 * sustituirla en tests/dispositivos sin telefonía.
 */
interface ContactDialer {
    suspend fun callOnce(
        contact: EscalationContact,
        perContactRingSeconds: Int,
        useSpeakerphone: Boolean,
        isAlertStillPending: suspend () -> Boolean,
    ): Boolean
}
