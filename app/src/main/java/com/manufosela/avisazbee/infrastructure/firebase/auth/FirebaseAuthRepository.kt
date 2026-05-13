package com.manufosela.avisazbee.infrastructure.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.manufosela.avisazbee.features.auth.domain.AppUser
import com.manufosela.avisazbee.features.auth.domain.AuthFailure
import com.manufosela.avisazbee.features.auth.domain.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override fun authStateChanges(): Flow<AppUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAppUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AppUser?
        get() = auth.currentUser?.toAppUser()

    override suspend fun signInWithGoogle(): AppUser {
        throw AuthFailure(
            code = "unknown",
            message = "TODO: requiere Activity context, se cablea con Credentials API en Fase 3",
        )
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

private fun FirebaseUser.toAppUser(): AppUser = AppUser(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    createdAt = metadata?.creationTimestamp
        ?.let(Instant::ofEpochMilli)
        ?: Instant.EPOCH,
)
