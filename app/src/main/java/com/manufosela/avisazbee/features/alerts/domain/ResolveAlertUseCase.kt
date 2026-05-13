package com.manufosela.avisazbee.features.alerts.domain

import javax.inject.Inject

/**
 * Closes the alert with a final message.
 *
 * The resolution message is required by product decision: the UI must show
 * a closing dialog and only enable the confirm button when there is at
 * least one non-blank character.
 */
class ResolveAlertUseCase @Inject constructor(
    private val repository: AlertRepository,
) {
    suspend operator fun invoke(
        alertId: String,
        uid: String,
        resolution: String,
    ): Alert {
        val trimmed = resolution.trim()
        if (trimmed.isEmpty()) {
            throw AlertFailure(
                "invalid_resolution",
                "Resolution message is required.",
            )
        }
        return repository.resolve(alertId, uid, trimmed)
    }
}
