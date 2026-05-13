package com.manufosela.avisazbee.features.channels.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelDialer
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChannelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChannelRepository {

    override suspend fun createChannel(
        name: String,
        ownerUid: String,
        inviteCode: String,
    ): Channel {
        val docRef = firestore.collection(CHANNELS).document()
        val now = FieldValue.serverTimestamp()
        val batch = firestore.batch()
        batch.set(
            docRef,
            mapOf(
                FIELD_NAME to name,
                FIELD_OWNER_UID to ownerUid,
                FIELD_INVITE_CODE to inviteCode,
                FIELD_INVITE_ENABLED to true,
                FIELD_CREATED_AT to now,
            ),
        )
        batch.set(
            docRef.collection(MEMBERS).document(ownerUid),
            mapOf(
                FIELD_ROLE to ChannelRole.OWNER.toFirestore(),
                FIELD_JOINED_AT to now,
                FIELD_NOTIFICATIONS_ENABLED to true,
            ),
        )
        batch.commit().await()
        val snapshot = docRef.get().await()
        return snapshot.toChannel()
            ?: throw ChannelFailure("unknown", "channel disappeared right after create")
    }

    override fun watchChannelsForUser(uid: String): Flow<List<Channel>> = callbackFlow {
        val registration = firestore.collectionGroup(MEMBERS)
            .whereEqualTo(FieldPath.documentId(), uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val channelIds = snapshot.documents.mapNotNull { it.reference.parent.parent?.id }
                if (channelIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                channelIds
                    .chunked(FIRESTORE_WHERE_IN_LIMIT)
                    .forEach { ids ->
                        firestore.collection(CHANNELS)
                            .whereIn(FieldPath.documentId(), ids)
                            .get()
                            .addOnSuccessListener { result ->
                                val channels = result.documents.mapNotNull { it.toChannel() }
                                trySend(channels)
                            }
                    }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun findById(channelId: String): Channel? =
        firestore.collection(CHANNELS).document(channelId).get().await().toChannel()

    override suspend fun findByInviteCode(code: String): Channel? {
        val result = firestore.collection(CHANNELS)
            .whereEqualTo(FIELD_INVITE_CODE, code)
            .whereEqualTo(FIELD_INVITE_ENABLED, true)
            .limit(1)
            .get()
            .await()
        return result.documents.firstOrNull()?.toChannel()
    }

    override suspend fun setInviteCode(channelId: String, code: String) {
        firestore.collection(CHANNELS).document(channelId)
            .update(FIELD_INVITE_CODE, code)
            .await()
    }

    override suspend fun setInviteEnabled(channelId: String, enabled: Boolean) {
        firestore.collection(CHANNELS).document(channelId)
            .update(FIELD_INVITE_ENABLED, enabled)
            .await()
    }

    override suspend fun findMember(channelId: String, uid: String): ChannelMember? =
        firestore.collection(CHANNELS).document(channelId)
            .collection(MEMBERS).document(uid)
            .get().await().toMember()

    override suspend fun addMember(channelId: String, uid: String, role: ChannelRole) {
        firestore.collection(CHANNELS).document(channelId)
            .collection(MEMBERS).document(uid)
            .set(
                mapOf(
                    FIELD_ROLE to role.toFirestore(),
                    FIELD_JOINED_AT to FieldValue.serverTimestamp(),
                    FIELD_NOTIFICATIONS_ENABLED to true,
                ),
            )
            .await()
    }

    override suspend fun updateMemberRole(
        channelId: String,
        uid: String,
        role: ChannelRole,
    ) {
        val membersRef = firestore.collection(CHANNELS).document(channelId).collection(MEMBERS)
        firestore.runTransaction { tx ->
            val memberRef = membersRef.document(uid)
            val current = tx.get(memberRef)
            val currentRole = current.getString(FIELD_ROLE)?.toChannelRole()
                ?: throw ChannelFailure("not_member")
            if (currentRole == role) return@runTransaction
            if (currentRole == ChannelRole.OWNER && role != ChannelRole.OWNER) {
                throw ChannelFailure(
                    code = "not_authorized",
                    message = "demoting the only owner would orphan the channel",
                )
            }
            tx.update(memberRef, FIELD_ROLE, role.toFirestore())
        }.await()
    }

    override fun watchMembers(channelId: String): Flow<List<ChannelMember>> = callbackFlow {
        val registration = firestore.collection(CHANNELS).document(channelId)
            .collection(MEMBERS)
            .orderBy(FIELD_JOINED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toMember() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun watchDialers(channelId: String): Flow<List<ChannelDialer>> = callbackFlow {
        val registration = firestore.collection(CHANNELS).document(channelId)
            .collection(DIALERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toDialer(channelId) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun findDialer(channelId: String, deviceId: String): ChannelDialer? =
        firestore.collection(CHANNELS).document(channelId)
            .collection(DIALERS).document(deviceId)
            .get().await().toDialer(channelId)

    override suspend fun addDialer(channelId: String, deviceId: String, addedBy: String) {
        firestore.collection(CHANNELS).document(channelId)
            .collection(DIALERS).document(deviceId)
            .set(
                mapOf(
                    FIELD_ADDED_BY to addedBy,
                    FIELD_ADDED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun removeDialer(channelId: String, deviceId: String) {
        firestore.collection(CHANNELS).document(channelId)
            .collection(DIALERS).document(deviceId)
            .delete()
            .await()
    }

    private companion object {
        const val CHANNELS = "channels"
        const val MEMBERS = "members"
        const val DIALERS = "dialers"

        const val FIELD_NAME = "name"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_INVITE_CODE = "inviteCode"
        const val FIELD_INVITE_ENABLED = "inviteEnabled"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_ROLE = "role"
        const val FIELD_JOINED_AT = "joinedAt"
        const val FIELD_NOTIFICATIONS_ENABLED = "notificationsEnabled"
        const val FIELD_ADDED_BY = "addedBy"
        const val FIELD_ADDED_AT = "addedAt"

        const val FIRESTORE_WHERE_IN_LIMIT = 30
    }
}

private fun ChannelRole.toFirestore(): String = when (this) {
    ChannelRole.OWNER -> "owner"
    ChannelRole.ADMIN -> "admin"
    ChannelRole.RECEIVER -> "receiver"
}

private fun String.toChannelRole(): ChannelRole = when (this) {
    "owner" -> ChannelRole.OWNER
    "admin" -> ChannelRole.ADMIN
    "receiver" -> ChannelRole.RECEIVER
    else -> throw ChannelFailure("unknown", "unknown role: $this")
}

private fun DocumentSnapshot.toChannel(): Channel? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    val ownerUid = getString("ownerUid") ?: return null
    val inviteCode = getString("inviteCode") ?: return null
    val inviteEnabled = getBoolean("inviteEnabled") ?: true
    val createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.EPOCH
    return Channel(
        id = id,
        name = name,
        ownerUid = ownerUid,
        inviteCode = inviteCode,
        inviteEnabled = inviteEnabled,
        createdAt = createdAt,
    )
}

private fun DocumentSnapshot.toMember(): ChannelMember? {
    if (!exists()) return null
    val role = getString("role")?.toChannelRole() ?: return null
    val joinedAt = getTimestamp("joinedAt")?.toInstant() ?: Instant.EPOCH
    val notificationsEnabled = getBoolean("notificationsEnabled") ?: true
    return ChannelMember(
        uid = id,
        role = role,
        joinedAt = joinedAt,
        notificationsEnabled = notificationsEnabled,
    )
}

private fun DocumentSnapshot.toDialer(channelId: String): ChannelDialer? {
    if (!exists()) return null
    val addedBy = getString("addedBy") ?: return null
    val addedAt = getTimestamp("addedAt")?.toInstant() ?: Instant.EPOCH
    return ChannelDialer(
        channelId = channelId,
        deviceId = id,
        addedBy = addedBy,
        addedAt = addedAt,
    )
}
