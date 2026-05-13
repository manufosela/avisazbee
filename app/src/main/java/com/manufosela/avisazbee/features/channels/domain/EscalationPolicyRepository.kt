package com.manufosela.avisazbee.features.channels.domain

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the per-channel escalation policy data source. Stored at
 * `channels/{channelId}/config/escalation`.
 */
interface EscalationPolicyRepository {
    /** Returns the current policy. Returns [EscalationPolicy.Disabled] when
     *  the channel has no stored policy yet. */
    suspend fun getFor(channelId: String): EscalationPolicy

    fun watchFor(channelId: String): Flow<EscalationPolicy>

    /** Upserts [policy] for [channelId]. */
    suspend fun update(channelId: String, policy: EscalationPolicy)
}
