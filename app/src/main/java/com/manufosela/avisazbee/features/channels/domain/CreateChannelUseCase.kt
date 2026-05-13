package com.manufosela.avisazbee.features.channels.domain

import com.manufosela.avisazbee.shared.RandomTokenGenerator
import javax.inject.Inject
import javax.inject.Named

/**
 * Creates a brand-new channel owned by [ownerUid] and generates its initial
 * invite code. Membership and `inviteEnabled: true` are set atomically by
 * the repository implementation.
 */
class CreateChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
    @Named("inviteCodeGenerator")
    private val inviteCodeGenerator: RandomTokenGenerator = RandomTokenGenerator.invite(),
) {
    suspend operator fun invoke(name: String, ownerUid: String): Channel {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw ChannelFailure("invalid_name", "Channel name is required.")
        }
        return repository.createChannel(
            name = trimmed,
            ownerUid = ownerUid,
            inviteCode = inviteCodeGenerator.generate(),
        )
    }
}
