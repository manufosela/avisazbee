package com.manufosela.avisazbee.infrastructure.firebase.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.manufosela.avisazbee.R
import com.manufosela.avisazbee.features.users.domain.NotificationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Avisos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Avisos de los botones Zigbee de tus canales."
                enableVibration(true)
                enableLights(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun showAlert(
        alertId: String,
        channelName: String,
        title: String,
        message: String,
        mode: NotificationMode,
    ) {
        ensureChannels()
        val nm = context.getSystemService<NotificationManager>() ?: return
        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("$channelName · $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .applyMode(mode)
        nm.notify(alertId.hashCode(), builder.build())
    }

    private fun NotificationCompat.Builder.applyMode(mode: NotificationMode) = apply {
        when (mode) {
            NotificationMode.SOUND -> setDefaults(NotificationCompat.DEFAULT_SOUND)
            NotificationMode.VIBRATION -> setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            NotificationMode.BOTH -> setDefaults(NotificationCompat.DEFAULT_ALL)
            NotificationMode.SILENT -> setSilent(true)
        }
    }

    private companion object {
        const val ALERT_CHANNEL_ID = "avisazbee_alerts"
    }
}
