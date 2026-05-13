package com.manufosela.avisazbee.features.devices.domain

import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Registers or updates the current device so it can receive alerts.
 *
 * Ownership and channel subscriptions are preserved across re-registrations:
 * a fresh call only refreshes `fcmToken`, `platform`, `name`, `enabled` and
 * `updatedAt`. Subscriptions are managed by dedicated use cases.
 */
class RegisterDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(
        deviceId: String,
        ownerUid: String,
        fcmToken: String,
        platform: DevicePlatform,
        name: String,
        enabled: Boolean = true,
    ): Device {
        val now: Instant = clock.instant()
        val existing = repository.findById(deviceId)
        val device = Device(
            id = deviceId,
            ownerUid = ownerUid,
            fcmToken = fcmToken,
            platform = platform,
            name = name,
            enabled = enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            subscribedChannels = existing?.subscribedChannels ?: emptyList(),
        )
        repository.upsert(device)
        return device
    }
}
