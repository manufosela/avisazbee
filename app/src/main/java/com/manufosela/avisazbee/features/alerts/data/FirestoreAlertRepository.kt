package com.manufosela.avisazbee.features.alerts.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.manufosela.avisazbee.features.alerts.domain.Alert
import com.manufosela.avisazbee.features.alerts.domain.AlertFailure
import com.manufosela.avisazbee.features.alerts.domain.AlertRepository
import com.manufosela.avisazbee.features.alerts.domain.AlertStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreAlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AlertRepository {

    override fun watchByChannel(channelId: String): Flow<List<Alert>> = callbackFlow {
        val registration = firestore.collection(ALERTS)
            .whereEqualTo(FIELD_CHANNEL_ID, channelId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toAlert() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun findById(alertId: String): Alert? =
        firestore.collection(ALERTS).document(alertId).get().await().toAlert()

    override suspend fun claim(alertId: String, uid: String): Alert {
        val docRef = firestore.collection(ALERTS).document(alertId)
        firestore.runTransaction { tx ->
            val current = tx.get(docRef)
            if (!current.exists()) throw AlertFailure("not_found")
            when (current.getString(FIELD_STATUS)) {
                STATUS_CLAIMED -> throw AlertFailure("already_claimed")
                STATUS_RESOLVED -> throw AlertFailure("already_resolved")
            }
            tx.update(
                docRef,
                mapOf(
                    FIELD_STATUS to STATUS_CLAIMED,
                    FIELD_CLAIMED_BY to uid,
                    FIELD_CLAIMED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        return docRef.get().await().toAlert()
            ?: throw AlertFailure("not_found")
    }

    override suspend fun resolve(
        alertId: String,
        uid: String,
        resolution: String,
    ): Alert {
        val trimmed = resolution.trim()
        if (trimmed.isEmpty()) throw AlertFailure("invalid_resolution")
        val docRef = firestore.collection(ALERTS).document(alertId)
        firestore.runTransaction { tx ->
            val current = tx.get(docRef)
            if (!current.exists()) throw AlertFailure("not_found")
            if (current.getString(FIELD_STATUS) == STATUS_RESOLVED) {
                throw AlertFailure("already_resolved")
            }
            tx.update(
                docRef,
                mapOf(
                    FIELD_STATUS to STATUS_RESOLVED,
                    FIELD_RESOLVED_BY to uid,
                    FIELD_RESOLVED_AT to FieldValue.serverTimestamp(),
                    FIELD_RESOLUTION to trimmed,
                ),
            )
        }.await()
        return docRef.get().await().toAlert()
            ?: throw AlertFailure("not_found")
    }

    private companion object {
        const val ALERTS = "alerts"

        const val FIELD_CHANNEL_ID = "channelId"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_STATUS = "status"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_RESOLVED_BY = "resolvedBy"
        const val FIELD_RESOLVED_AT = "resolvedAt"
        const val FIELD_RESOLUTION = "resolution"

        const val STATUS_CLAIMED = "claimed"
        const val STATUS_RESOLVED = "resolved"
    }
}

private fun DocumentSnapshot.toAlert(): Alert? {
    if (!exists()) return null
    val channelId = getString("channelId") ?: return null
    val buttonId = getString("buttonId") ?: return null
    val buttonName = getString("buttonName") ?: return null
    val type = getString("type") ?: return null
    val title = getString("title") ?: return null
    val message = getString("message") ?: return null
    val source = getString("source") ?: return null
    val createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.EPOCH
    val status = when (getString("status")) {
        "pending" -> AlertStatus.PENDING
        "claimed" -> AlertStatus.CLAIMED
        "resolved" -> AlertStatus.RESOLVED
        else -> return null
    }
    return Alert(
        id = id,
        channelId = channelId,
        buttonId = buttonId,
        buttonName = buttonName,
        type = type,
        title = title,
        message = message,
        source = source,
        createdAt = createdAt,
        status = status,
        claimedBy = getString("claimedBy"),
        claimedAt = getTimestamp("claimedAt")?.toInstant(),
        resolvedBy = getString("resolvedBy"),
        resolvedAt = getTimestamp("resolvedAt")?.toInstant(),
        resolution = getString("resolution"),
        repeatIntervalSeconds = (getLong("repeatIntervalSeconds") ?: 30L).toInt(),
        repeatCount = (getLong("repeatCount") ?: 0L).toInt(),
        lastRepeatAt = getTimestamp("lastRepeatAt")?.toInstant(),
    )
}
