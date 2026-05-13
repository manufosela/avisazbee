package com.manufosela.avisazbee.features.buttons.domain

import kotlinx.coroutines.flow.Flow

/**
 * Failures the buttons data layer can surface to the domain.
 *
 * Codes: `not_found`, `not_authorized`, `invalid_button_id`,
 * `invalid_button_name`, `already_exists`, `unknown`.
 */
class ButtonFailure(
    val code: String,
    message: String? = null,
) : Exception(message ?: "ButtonFailure($code)")

interface ButtonRepository {
    suspend fun findById(buttonId: String): Button?

    fun watchByChannel(channelId: String): Flow<List<Button>>

    /**
     * Persists a brand-new button. Implementations must:
     *   - fail with `already_exists` if the id is taken;
     *   - hash [plainSecret] before storing it.
     *
     * Returns a [NewButton] carrying the plain secret once.
     */
    suspend fun create(
        id: String,
        name: String,
        channelId: String,
        createdBy: String,
        plainSecret: String,
    ): NewButton

    suspend fun setEnabled(buttonId: String, enabled: Boolean)

    suspend fun rename(buttonId: String, newName: String)

    /**
     * Rotates the HTTP secret. Returns the new plain secret once so the UI
     * can show it; the old secret stops working immediately.
     */
    suspend fun rotateSecret(
        buttonId: String,
        plainSecret: String,
    ): NewButton

    suspend fun delete(buttonId: String)
}
