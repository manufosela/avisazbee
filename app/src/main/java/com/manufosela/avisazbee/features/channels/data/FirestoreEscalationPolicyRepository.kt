package com.manufosela.avisazbee.features.channels.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.manufosela.avisazbee.features.channels.domain.EscalationContact
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicy
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicyRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreEscalationPolicyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : EscalationPolicyRepository {

    override suspend fun getFor(channelId: String): EscalationPolicy =
        docRef(channelId).get().await().toPolicy() ?: EscalationPolicy.Disabled

    override fun watchFor(channelId: String): Flow<EscalationPolicy> = callbackFlow {
        val registration = docRef(channelId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toPolicy() ?: EscalationPolicy.Disabled)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun update(channelId: String, policy: EscalationPolicy) {
        docRef(channelId).set(policy.toFirestoreMap()).await()
    }

    private fun docRef(channelId: String) = firestore
        .collection("channels").document(channelId)
        .collection("config").document("escalation")
}

private fun EscalationPolicy.toFirestoreMap(): Map<String, Any?> = mapOf(
    "enabled" to enabled,
    "escalateAfterSeconds" to escalateAfterSeconds,
    "contacts" to contacts.map { mapOf("name" to it.name, "phoneE164" to it.phoneE164) },
    "perContactRingSeconds" to perContactRingSeconds,
    "useSpeakerphone" to useSpeakerphone,
    "loopUntilAnswered" to loopUntilAnswered,
)

private fun DocumentSnapshot.toPolicy(): EscalationPolicy? {
    if (!exists()) return null
    val rawContacts = get("contacts") as? List<*> ?: emptyList<Any?>()
    val contacts = rawContacts.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val name = map["name"] as? String ?: return@mapNotNull null
        val phone = map["phoneE164"] as? String ?: return@mapNotNull null
        EscalationContact(name = name, phoneE164 = phone)
    }
    return EscalationPolicy(
        enabled = getBoolean("enabled") ?: false,
        escalateAfterSeconds = (getLong("escalateAfterSeconds") ?: 180L).toInt(),
        contacts = contacts,
        perContactRingSeconds = (getLong("perContactRingSeconds") ?: 25L).toInt(),
        useSpeakerphone = getBoolean("useSpeakerphone") ?: true,
        loopUntilAnswered = getBoolean("loopUntilAnswered") ?: true,
    )
}
