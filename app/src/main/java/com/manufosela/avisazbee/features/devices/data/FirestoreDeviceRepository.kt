package com.manufosela.avisazbee.features.devices.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.manufosela.avisazbee.features.devices.domain.Device
import com.manufosela.avisazbee.features.devices.domain.DevicePlatform
import com.manufosela.avisazbee.features.devices.domain.DeviceRepository
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDeviceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DeviceRepository {

    override suspend fun findById(id: String): Device? =
        firestore.collection(DEVICES).document(id).get().await().toDevice()

    override suspend fun upsert(device: Device) {
        firestore.collection(DEVICES).document(device.id)
            .set(device.toFirestoreMap())
            .await()
    }

    private companion object {
        const val DEVICES = "devices"
    }
}

private fun Device.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerUid" to ownerUid,
    "fcmToken" to fcmToken,
    "platform" to platform.toFirestore(),
    "name" to name,
    "enabled" to enabled,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
    "updatedAt" to Timestamp(updatedAt.epochSecond, updatedAt.nano),
    "subscribedChannels" to subscribedChannels,
)

private fun DevicePlatform.toFirestore(): String = when (this) {
    DevicePlatform.ANDROID -> "android"
    DevicePlatform.IOS -> "ios"
}

private fun String.toDevicePlatform(): DevicePlatform = when (this) {
    "android" -> DevicePlatform.ANDROID
    "ios" -> DevicePlatform.IOS
    else -> DevicePlatform.ANDROID
}

private fun DocumentSnapshot.toDevice(): Device? {
    if (!exists()) return null
    val ownerUid = getString("ownerUid") ?: return null
    val fcmToken = getString("fcmToken") ?: return null
    val platform = getString("platform")?.toDevicePlatform() ?: DevicePlatform.ANDROID
    val name = getString("name") ?: return null
    val enabled = getBoolean("enabled") ?: true
    val createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.EPOCH
    val updatedAt = getTimestamp("updatedAt")?.toInstant() ?: createdAt
    @Suppress("UNCHECKED_CAST")
    val subscribedChannels = (get("subscribedChannels") as? List<String>).orEmpty()
    return Device(
        id = id,
        ownerUid = ownerUid,
        fcmToken = fcmToken,
        platform = platform,
        name = name,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        subscribedChannels = subscribedChannels,
    )
}
