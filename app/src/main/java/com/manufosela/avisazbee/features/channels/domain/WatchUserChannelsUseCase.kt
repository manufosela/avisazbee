package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the channels the given user owns or is subscribed to. */
class WatchUserChannelsUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(uid: String): Flow<List<Channel>> =
        repository.watchChannelsForUser(uid)
}
