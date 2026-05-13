package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/** Removes the dialer flag from a device on the given channel. */
class RemoveDialerUseCase @Inject constructor(
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
                "Only owner or admin can remove a dialer.",
            )
        }
        repository.removeDialer(channelId, deviceId)
    }
}
