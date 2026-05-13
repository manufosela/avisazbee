package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/** Demotes an admin back to receiver. Only the channel owner can do it. */
class DemoteAdminToReceiverUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(
        channelId: String,
        callerUid: String,
        targetUid: String,
    ) {
        val channel = repository.findById(channelId)
            ?: throw ChannelFailure("not_found")
        if (channel.ownerUid != callerUid) {
            throw ChannelFailure(
                "not_authorized",
                "Only the channel owner can demote admins.",
            )
        }
        if (targetUid == channel.ownerUid) throw ChannelFailure("is_owner")
        val target = repository.findMember(channelId, targetUid)
            ?: throw ChannelFailure("not_member")
        if (target.role == ChannelRole.RECEIVER) return
        repository.updateMemberRole(channelId, targetUid, ChannelRole.RECEIVER)
    }
}
