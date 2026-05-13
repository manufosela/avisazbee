package com.manufosela.avisazbee.features.channels.domain

import java.time.Instant

/**
 * Flags a device as a "dependent dialer" for a specific channel. Such a
 * device runs the auto-call escalation when an alert is not claimed in time.
 */
data class ChannelDialer(
    val channelId: String,
    val deviceId: String,
    /** uid of the owner or admin that flagged the device as dialer. */
    val addedBy: String,
    val addedAt: Instant,
)
