package com.manufosela.avisazbee.features.users.domain

import javax.inject.Inject

/**
 * Changes the notification style for the given user.
 *
 * Reads the current preferences first to preserve any future fields when we
 * add them (sound name, per-channel overrides…). A blind write would erase
 * those.
 */
class UpdateNotificationModeUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke(
        uid: String,
        mode: NotificationMode,
    ): NotificationPreferences {
        val current = repository.getFor(uid)
        val updated = current.copy(mode = mode)
        repository.update(uid, updated)
        return updated
    }
}
