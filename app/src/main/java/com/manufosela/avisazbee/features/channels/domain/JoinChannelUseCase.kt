package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/**
 * Adds the current user to a channel using its invite code.
 *
 * Validation order:
 *   1. Code resolves to a channel.
 *   2. The channel currently accepts new joins (`inviteEnabled`).
 *   3. The caller is not the owner.
 *   4. The caller is not already a member.
 */
class JoinChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(uid: String, inviteCode: String): Channel {
        val channel = repository.findByInviteCode(inviteCode)
            ?: throw ChannelFailure("invalid_code")
        if (!channel.inviteEnabled) throw ChannelFailure("invites_disabled")
        if (channel.ownerUid == uid) throw ChannelFailure("is_owner")
        val existing = repository.findMember(channel.id, uid)
        if (existing != null) throw ChannelFailure("already_member")
        repository.addMember(channel.id, uid, ChannelRole.RECEIVER)
        return channel
    }
}
