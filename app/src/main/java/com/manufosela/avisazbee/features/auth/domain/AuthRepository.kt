package com.manufosela.avisazbee.features.auth.domain

import kotlinx.coroutines.flow.Flow

/**
 * Coarse-grained failure for the authentication flow. The data layer maps
 * SDK-specific errors (network, cancelled flow, account-disabled…) to one
 * of the known codes so the rest of the app stays decoupled from Firebase.
 */
class AuthFailure(
    /** One of: `cancelled`, `network`, `unknown`. */
    val code: String,
    message: String? = null,
) : Exception(message ?: "AuthFailure($code)")

/**
 * Contract for the authentication layer. Implementations live under
 * `infrastructure/firebase/` (Phase 2).
 */
interface AuthRepository {
    /**
     * Emits the current authenticated user or `null` when signed out.
     *
     * MUST emit synchronously with the latest known value on collect so the
     * UI can decide between splash, sign-in screen and home without flicker.
     */
    fun authStateChanges(): Flow<AppUser?>

    /** Snapshot of the authenticated user, or `null` when signed out. */
    val currentUser: AppUser?

    /** Launches the Google Sign-In flow. Throws [AuthFailure] on failure. */
    suspend fun signInWithGoogle(): AppUser

    /** Clears the current session locally and in Firebase Auth. */
    suspend fun signOut()
}
