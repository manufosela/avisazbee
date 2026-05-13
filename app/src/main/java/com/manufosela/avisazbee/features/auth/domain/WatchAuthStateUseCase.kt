package com.manufosela.avisazbee.features.auth.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams the current authenticated user so the UI can react to login and
 * logout events without coupling to Firebase.
 */
class WatchAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<AppUser?> = repository.authStateChanges()
}
