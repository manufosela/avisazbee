package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/**
 * Promotes a receiver to admin. Only the channel owner can perform this
 * action; the use case checks the invariant explicitly so a buggy UI cannot
 * let an admin promote another admin.
 */
class PromoteMemberToAdminUseCase @Inject constructor(
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
                "Only the channel owner can promote admins.",
            )
        }
        if (targetUid == channel.ownerUid) {
            throw ChannelFailure(
                "is_owner",
                "The owner cannot be demoted to admin.",
            )
        }
        val target = repository.findMember(channelId, targetUid)
            ?: throw ChannelFailure("not_member")
        if (target.role == ChannelRole.ADMIN) return
        repository.updateMemberRole(channelId, targetUid, ChannelRole.ADMIN)
    }
}
