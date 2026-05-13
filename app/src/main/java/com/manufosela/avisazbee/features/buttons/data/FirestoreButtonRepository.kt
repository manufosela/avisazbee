package com.manufosela.avisazbee.features.buttons.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.manufosela.avisazbee.features.buttons.domain.Button
import com.manufosela.avisazbee.features.buttons.domain.ButtonFailure
import com.manufosela.avisazbee.features.buttons.domain.ButtonRepository
import com.manufosela.avisazbee.features.buttons.domain.NewButton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreButtonRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ButtonRepository {

    override suspend fun findById(buttonId: String): Button? =
        firestore.collection(BUTTONS).document(buttonId).get().await().toButton()

    override fun watchByChannel(channelId: String): Flow<List<Button>> = callbackFlow {
        val registration = firestore.collection(BUTTONS)
            .whereEqualTo(FIELD_CHANNEL_ID, channelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toButton() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun create(
        id: String,
        name: String,
        channelId: String,
        createdBy: String,
        plainSecret: String,
    ): NewButton {
        val docRef = firestore.collection(BUTTONS).document(id)
        val hashed = BCrypt.hashpw(plainSecret, BCrypt.gensalt(BCRYPT_COST))
        firestore.runTransaction { tx ->
            val current = tx.get(docRef)
            if (current.exists()) {
                throw ButtonFailure(
                    "already_exists",
                    "A button with id '$id' is already registered.",
                )
            }
            tx.set(
                docRef,
                mapOf(
                    FIELD_NAME to name,
                    FIELD_CHANNEL_ID to channelId,
                    FIELD_ENABLED to true,
                    FIELD_HASHED_SECRET to hashed,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_CREATED_BY to createdBy,
                ),
            )
        }.await()
        val stored = docRef.get().await().toButton()
            ?: throw ButtonFailure("unknown", "button disappeared right after create")
        return NewButton(metadata = stored, plainSecret = plainSecret)
    }

    override suspend fun setEnabled(buttonId: String, enabled: Boolean) {
        firestore.collection(BUTTONS).document(buttonId)
            .update(FIELD_ENABLED, enabled)
            .await()
    }

    override suspend fun rename(buttonId: String, newName: String) {
        firestore.collection(BUTTONS).document(buttonId)
            .update(FIELD_NAME, newName)
            .await()
    }

    override suspend fun rotateSecret(
        buttonId: String,
        plainSecret: String,
    ): NewButton {
        val docRef = firestore.collection(BUTTONS).document(buttonId)
        val hashed = BCrypt.hashpw(plainSecret, BCrypt.gensalt(BCRYPT_COST))
        firestore.runTransaction { tx ->
            val current = tx.get(docRef)
            if (!current.exists()) throw ButtonFailure("not_found")
            tx.update(docRef, FIELD_HASHED_SECRET, hashed)
        }.await()
        val updated = docRef.get().await().toButton()
            ?: throw ButtonFailure("not_found")
        return NewButton(metadata = updated, plainSecret = plainSecret)
    }

    override suspend fun delete(buttonId: String) {
        firestore.collection(BUTTONS).document(buttonId).delete().await()
    }

    private companion object {
        const val BUTTONS = "buttons"
        const val BCRYPT_COST = 12

        const val FIELD_NAME = "name"
        const val FIELD_CHANNEL_ID = "channelId"
        const val FIELD_ENABLED = "enabled"
        const val FIELD_HASHED_SECRET = "hashedSecret"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_CREATED_BY = "createdBy"
    }
}

private fun DocumentSnapshot.toButton(): Button? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    val channelId = getString("channelId") ?: return null
    val enabled = getBoolean("enabled") ?: true
    val hashedSecret = getString("hashedSecret") ?: return null
    val createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.EPOCH
    val createdBy = getString("createdBy") ?: return null
    return Button(
        id = id,
        name = name,
        channelId = channelId,
        enabled = enabled,
        createdAt = createdAt,
        createdBy = createdBy,
        hashedSecret = hashedSecret,
    )
}
