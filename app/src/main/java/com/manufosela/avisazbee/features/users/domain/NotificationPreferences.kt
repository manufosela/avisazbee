package com.manufosela.avisazbee.features.users.domain

/**
 * How the device should signal an incoming alert.
 *
 * Stored per-user so it survives reinstalls and applies to every device the
 * user owns. The actual ringtone/vibration pattern is decided by the
 * presentation layer; the domain only carries intent.
 */
enum class NotificationMode { SOUND, VIBRATION, BOTH, SILENT }

/**
 * Per-user notification preferences. Kept in its own model (rather than a
 * field on [com.manufosela.avisazbee.features.auth.domain.AppUser]) so it
 * can live in its own document with its own security rules and grow without
 * polluting the auth payload.
 */
data class NotificationPreferences(
    val mode: NotificationMode = NotificationMode.BOTH,
) {
    companion object {
        val Defaults: NotificationPreferences = NotificationPreferences()
    }
}
