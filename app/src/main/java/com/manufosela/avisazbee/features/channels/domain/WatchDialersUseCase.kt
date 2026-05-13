package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchDialersUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    operator fun invoke(channelId: String): Flow<List<ChannelDialer>> =
        repository.watchDialers(channelId)
}
