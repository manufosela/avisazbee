package com.manufosela.avisazbee.features.users.domain

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the per-user preferences data source.
 *
 * Implementations live in `features/users/data/` (Phase 2). Each method is
 * scoped by `uid`: callers MUST supply the authenticated user's id; the
 * implementation MUST NOT read it from ambient state.
 */
interface UserPreferencesRepository {
    /** One-shot read. Returns [NotificationPreferences.Defaults] when the
     *  user has no document yet. */
    suspend fun getFor(uid: String): NotificationPreferences

    /** Live stream. Emits defaults until the first persisted value is read. */
    fun watchFor(uid: String): Flow<NotificationPreferences>

    /** Upserts the preferences document for [uid]. */
    suspend fun update(uid: String, preferences: NotificationPreferences)
}
