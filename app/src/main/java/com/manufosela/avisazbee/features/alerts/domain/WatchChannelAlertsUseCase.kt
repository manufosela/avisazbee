package com.manufosela.avisazbee.features.alerts.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchChannelAlertsUseCase @Inject constructor(
    private val repository: AlertRepository,
) {
    operator fun invoke(channelId: String): Flow<List<Alert>> =
        repository.watchByChannel(channelId)
}
