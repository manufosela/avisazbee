package com.manufosela.avisazbee.features.devices.domain

import java.time.Instant

enum class DevicePlatform { ANDROID, IOS }

/**
 * Domain model for a device that wants to receive alerts.
 *
 * A device is owned by exactly one authenticated user and can receive
 * alerts from any number of [subscribedChannels].
 */
data class Device(
    val id: String,
    val ownerUid: String,
    val fcmToken: String,
    val platform: DevicePlatform,
    val name: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val subscribedChannels: List<String> = emptyList(),
)
