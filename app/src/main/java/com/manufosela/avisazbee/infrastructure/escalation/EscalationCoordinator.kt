package com.manufosela.avisazbee.infrastructure.escalation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicy
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicyRepository
import com.manufosela.avisazbee.infrastructure.device.DeviceIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto único de entrada al motor de escalada.
 *
 * Reglas:
 * - solo se programa la escalada si este dispositivo aparece como dialer
 *   del canal (`channels/{id}/dialers/{deviceId}`);
 * - solo si la política está habilitada y tiene contactos;
 * - se usa work único por `alertId` para que múltiples pushes (reaviso)
 *   no encolen trabajos duplicados.
 */
@Singleton
class EscalationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider,
    private val channelRepository: ChannelRepository,
    private val policyRepository: EscalationPolicyRepository,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    suspend fun schedule(alertId: String, channelId: String) {
        val deviceId = deviceIdProvider.getOrCreate()
        val dialer = runCatching { channelRepository.findDialer(channelId, deviceId) }.getOrNull()
        if (dialer == null) return
        val policy: EscalationPolicy = runCatching { policyRepository.getFor(channelId) }
            .getOrElse { return }
        if (!policy.enabled || policy.contacts.isEmpty()) return

        val request = OneTimeWorkRequestBuilder<EscalationWork>()
            .setInputData(
                Data.Builder()
                    .putString(EscalationWork.KEY_ALERT_ID, alertId)
                    .putString(EscalationWork.KEY_CHANNEL_ID, channelId)
                    .build(),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(tagFor(alertId))
            .build()
        workManager.enqueueUniqueWork(workNameFor(alertId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(alertId: String) {
        workManager.cancelUniqueWork(workNameFor(alertId))
    }

    private fun workNameFor(alertId: String) = "escalation:$alertId"
    private fun tagFor(alertId: String) = "escalation:$alertId"
}
