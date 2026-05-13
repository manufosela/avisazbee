package com.manufosela.avisazbee.features.users.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams the current user's preferences so the FCM handler always sees the
 * latest [NotificationMode].
 */
class WatchUserPreferencesUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    operator fun invoke(uid: String): Flow<NotificationPreferences> =
        repository.watchFor(uid)
}
