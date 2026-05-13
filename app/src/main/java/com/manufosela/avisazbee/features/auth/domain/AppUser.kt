package com.manufosela.avisazbee.features.auth.domain

import java.time.Instant

/**
 * Authenticated user as exposed to the rest of the app.
 *
 * Mirrors the subset of Firebase Auth fields that the UI and use cases need.
 * Pure Kotlin: no SDK dependency.
 */
data class AppUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Instant,
)
