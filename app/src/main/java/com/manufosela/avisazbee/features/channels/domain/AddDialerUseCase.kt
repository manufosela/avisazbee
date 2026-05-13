package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/**
 * Flags a device as dialer for the given channel.
 *
 * Authorization: only the channel owner or an admin can run this. The
 * caller's role is verified against the channel membership; no ambient
 * state is consulted.
 */
class AddDialerUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    suspend operator fun invoke(
        channelId: String,
        callerUid: String,
        deviceId: String,
    ) {
        repository.findById(channelId)
            ?: throw ChannelFailure("not_found")
        val caller = repository.findMember(channelId, callerUid)
        if (caller == null ||
            (caller.role != ChannelRole.OWNER &&
                caller.role != ChannelRole.ADMIN)
        ) {
            throw ChannelFailure(
                "not_authorized",
                "Only owner or admin can mark a device as dialer.",
            )
        }
        val existing = repository.findDialer(channelId, deviceId)
        if (existing != null) return
        repository.addDialer(channelId, deviceId, addedBy = callerUid)
    }
}
