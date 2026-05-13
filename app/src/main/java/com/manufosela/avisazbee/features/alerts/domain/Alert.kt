package com.manufosela.avisazbee.features.alerts.domain

import java.time.Instant

/**
 * Lifecycle state of an [Alert]. Linear and one-way:
 *
 *   - [PENDING]: just created, nobody has taken charge yet.
 *   - [CLAIMED]: a single user pressed "I'll take this one".
 *   - [RESOLVED]: a user closed the alert with a final message. Terminal.
 *
 * `PENDING → RESOLVED` is also allowed for the "false alarm" case.
 */
enum class AlertStatus { PENDING, CLAIMED, RESOLVED }

/**
 * Domain model for an alert raised on a channel by an external source (a
 * Zigbee button via Home Assistant in the current scope).
 *
 * Includes the originating button's id and name for traceability.
 */
data class Alert(
    val id: String,
    val channelId: String,
    val buttonId: String,
    val buttonName: String,
    val type: String,
    val title: String,
    val message: String,
    val source: String,
    val createdAt: Instant,
    val status: AlertStatus = AlertStatus.PENDING,
    val claimedBy: String? = null,
    val claimedAt: Instant? = null,
    val resolvedBy: String? = null,
    val resolvedAt: Instant? = null,
    val resolution: String? = null,
    /** Seconds the backend waits between automatic repeats while pending. */
    val repeatIntervalSeconds: Int = 30,
    /** How many times the backend has already re-pushed this alert. */
    val repeatCount: Int = 0,
    val lastRepeatAt: Instant? = null,
) {
    init {
        require(repeatIntervalSeconds > 0)
        require(repeatCount >= 0)
    }

    val isPending: Boolean get() = status == AlertStatus.PENDING
    val isClaimed: Boolean get() = status == AlertStatus.CLAIMED
    val isResolved: Boolean get() = status == AlertStatus.RESOLVED
}
