package com.manufosela.avisazbee.infrastructure.device

import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.manufosela.avisazbee.features.auth.domain.WatchAuthStateUseCase
import com.manufosela.avisazbee.features.devices.domain.DevicePlatform
import com.manufosela.avisazbee.features.devices.domain.RegisterDeviceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires the current authenticated user to a stable device id + the latest
 * FCM token. Called from MainActivity.onCreate via [start]. Also exposes
 * [registerCurrentDevice] so the FCM service can refresh after onNewToken.
 */
@Singleton
class DeviceRegistrar @Inject constructor(
    private val deviceIdProvider: DeviceIdProvider,
    private val messaging: FirebaseMessaging,
    private val watchAuthState: WatchAuthStateUseCase,
    private val registerDevice: RegisterDeviceUseCase,
) {
    fun start(scope: CoroutineScope) {
        watchAuthState()
            .map { it?.uid }
            .distinctUntilChanged()
            .onEach { uid -> if (uid != null) registerCurrentDevice(uid) }
            .launchIn(scope)
    }

    suspend fun registerCurrentDevice(uid: String) {
        val deviceId = deviceIdProvider.getOrCreate()
        val token = runCatching { messaging.token.await() }.getOrNull() ?: return
        registerDevice(
            deviceId = deviceId,
            ownerUid = uid,
            fcmToken = token,
            platform = DevicePlatform.ANDROID,
            name = Build.MODEL ?: "Android",
        )
    }
}
