package com.manufosela.avisazbee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.manufosela.avisazbee.app.AvisazbeeApp
import com.manufosela.avisazbee.infrastructure.device.DeviceRegistrar
import com.manufosela.avisazbee.infrastructure.firebase.fcm.AlertNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity. Compose owns the entire UI tree, navigation included.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deviceRegistrar: DeviceRegistrar
    @Inject lateinit var notifications: AlertNotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        notifications.ensureChannels()
        deviceRegistrar.start(lifecycleScope)
        setContent {
            AvisazbeeApp()
        }
    }
}
