package com.manufosela.avisazbee.features.auth.domain

import javax.inject.Inject

/** Signs out the current user and clears local credentials. */
class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() = repository.signOut()
}
