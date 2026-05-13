package com.manufosela.avisazbee.features.alerts.domain

import kotlinx.coroutines.flow.Flow

/**
 * Failures the alerts data layer can surface to the domain.
 *
 * Implementations MUST perform claim/resolve as atomic Firestore
 * transactions so the corresponding `*_already_*` codes are returned
 * reliably under concurrent presses by two receivers.
 *
 * Codes: `already_claimed`, `already_resolved`, `not_found`,
 * `not_member`, `invalid_resolution`, `unknown`.
 */
class AlertFailure(
    val code: String,
    message: String? = null,
) : Exception(message ?: "AlertFailure($code)")

interface AlertRepository {
    /** Stream the alert history for a channel ordered by createdAt desc. */
    fun watchByChannel(channelId: String): Flow<List<Alert>>

    suspend fun findById(alertId: String): Alert?

    /**
     * Atomically transitions the alert from [AlertStatus.PENDING] to
     * [AlertStatus.CLAIMED] for [uid].
     */
    suspend fun claim(alertId: String, uid: String): Alert

    /**
     * Atomically transitions the alert to [AlertStatus.RESOLVED] with the
     * given resolution message. Allowed from both pending (false alarm) and
     * claimed.
     */
    suspend fun resolve(
        alertId: String,
        uid: String,
        resolution: String,
    ): Alert
}
