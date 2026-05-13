package com.manufosela.avisazbee.features.channels.domain

import com.manufosela.avisazbee.shared.RandomTokenGenerator
import javax.inject.Inject

/**
 * Replaces a channel's invite code with a fresh one. Returns the new code so
 * the UI can show it to the owner without an extra read.
 */
class RotateInviteCodeUseCase @Inject constructor(
    private val repository: ChannelRepository,
    private val inviteCodeGenerator: RandomTokenGenerator = RandomTokenGenerator.invite(),
) {
    suspend operator fun invoke(channelId: String): String {
        val code = inviteCodeGenerator.generate()
        repository.setInviteCode(channelId, code)
        return code
    }
}
