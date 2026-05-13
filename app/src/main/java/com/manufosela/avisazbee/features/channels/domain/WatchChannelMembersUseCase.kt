package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the live member list for [channelId]. */
class WatchChannelMembersUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(channelId: String): Flow<List<ChannelMember>> =
        repository.watchMembers(channelId)
}
