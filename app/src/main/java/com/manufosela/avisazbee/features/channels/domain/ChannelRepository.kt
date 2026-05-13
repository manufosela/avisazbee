package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow

/**
 * Failures the channels data layer can surface to the domain.
 *
 * Codes: `invalid_code`, `invites_disabled`, `already_member`, `is_owner`,
 * `not_found`, `not_member`, `not_authorized`, `invalid_name`,
 * `empty_contacts`, `invalid_contact_name`, `invalid_phone`, `unknown`.
 */
class ChannelFailure(
    val code: String,
    message: String? = null,
) : Exception(message ?: "ChannelFailure($code)")

interface ChannelRepository {
    /** Creates the channel and registers [ownerUid] as the owner member
     *  atomically. */
    suspend fun createChannel(
        name: String,
        ownerUid: String,
        inviteCode: String,
    ): Channel

    fun watchChannelsForUser(uid: String): Flow<List<Channel>>

    suspend fun findById(channelId: String): Channel?

    /** Implementations MUST ignore channels with `inviteEnabled == false`. */
    suspend fun findByInviteCode(code: String): Channel?

    suspend fun setInviteCode(channelId: String, code: String)

    suspend fun setInviteEnabled(channelId: String, enabled: Boolean)

    suspend fun findMember(channelId: String, uid: String): ChannelMember?

    suspend fun addMember(channelId: String, uid: String, role: ChannelRole)

    /** Transactional role swap. The implementation MUST prevent leaving the
     *  channel without an owner. */
    suspend fun updateMemberRole(
        channelId: String,
        uid: String,
        role: ChannelRole,
    )

    fun watchMembers(channelId: String): Flow<List<ChannelMember>>

    fun watchDialers(channelId: String): Flow<List<ChannelDialer>>

    suspend fun findDialer(channelId: String, deviceId: String): ChannelDialer?

    suspend fun addDialer(channelId: String, deviceId: String, addedBy: String)

    suspend fun removeDialer(channelId: String, deviceId: String)
}
