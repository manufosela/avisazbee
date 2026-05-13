package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchEscalationPolicyUseCase @Inject constructor(
    private val repository: EscalationPolicyRepository,
) {
    operator fun invoke(channelId: String): Flow<EscalationPolicy> =
        repository.watchFor(channelId)
}
