package com.manufosela.avisazbee.features.auth.domain

import javax.inject.Inject

/** Triggers the Google Sign-In flow and returns the authenticated user. */
class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AppUser = repository.signInWithGoogle()
}
