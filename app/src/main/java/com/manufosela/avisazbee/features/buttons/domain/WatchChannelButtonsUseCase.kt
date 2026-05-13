package com.manufosela.avisazbee.features.buttons.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchChannelButtonsUseCase @Inject constructor(
    private val repository: ButtonRepository,
) {
    operator fun invoke(channelId: String): Flow<List<Button>> =
        repository.watchByChannel(channelId)
}
