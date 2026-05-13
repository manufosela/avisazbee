package com.manufosela.avisazbee.features.users.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.manufosela.avisazbee.features.users.domain.NotificationMode
import com.manufosela.avisazbee.features.users.domain.NotificationPreferences
import com.manufosela.avisazbee.features.users.domain.UserPreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserPreferencesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserPreferencesRepository {

    override suspend fun getFor(uid: String): NotificationPreferences =
        docRef(uid).get().await().toPreferences() ?: NotificationPreferences.Defaults

    override fun watchFor(uid: String): Flow<NotificationPreferences> = callbackFlow {
        val registration = docRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toPreferences() ?: NotificationPreferences.Defaults)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun update(uid: String, preferences: NotificationPreferences) {
        docRef(uid).set(mapOf("mode" to preferences.mode.toFirestore())).await()
    }

    private fun docRef(uid: String) = firestore
        .collection("users").document(uid)
        .collection("preferences").document("notifications")
}

private fun NotificationMode.toFirestore(): String = when (this) {
    NotificationMode.SOUND -> "sound"
    NotificationMode.VIBRATION -> "vibration"
    NotificationMode.BOTH -> "both"
    NotificationMode.SILENT -> "silent"
}

private fun String.toNotificationMode(): NotificationMode = when (this) {
    "sound" -> NotificationMode.SOUND
    "vibration" -> NotificationMode.VIBRATION
    "both" -> NotificationMode.BOTH
    "silent" -> NotificationMode.SILENT
    else -> NotificationMode.BOTH
}

private fun DocumentSnapshot.toPreferences(): NotificationPreferences? {
    if (!exists()) return null
    val mode = getString("mode")?.toNotificationMode() ?: return null
    return NotificationPreferences(mode = mode)
}
