package com.manufosela.avisazbee.features.alerts.domain

import javax.inject.Inject

/**
 * "I'll take this one."
 *
 * Atomically marks the alert as being attended by [uid]. The check for
 * concurrency (two users tapping at the same time) happens inside the
 * repository implementation — the use case is intentionally thin.
 */
class ClaimAlertUseCase @Inject constructor(
    private val repository: AlertRepository,
) {
    suspend operator fun invoke(alertId: String, uid: String): Alert =
        repository.claim(alertId, uid)
}
