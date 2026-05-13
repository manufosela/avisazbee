package com.manufosela.avisazbee.features.buttons.domain

import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.shared.RandomTokenGenerator
import javax.inject.Inject

/**
 * Rotates the HTTP secret of a button. The old secret stops working
 * immediately; the new plain value is returned once so the UI can show it
 * for copy-paste into Home Assistant.
 */
class RotateButtonSecretUseCase @Inject constructor(
    private val buttons: ButtonRepository,
    private val channels: ChannelRepository,
    private val secretGenerator: RandomTokenGenerator = RandomTokenGenerator.secret(),
) {
    suspend operator fun invoke(
        buttonId: String,
        callerUid: String,
    ): NewButton {
        val id = IeeeAddress.normalise(buttonId)
        val button = buttons.findById(id)
            ?: throw ButtonFailure("not_found")
        val member = channels.findMember(button.channelId, callerUid)
        if (member == null ||
            (member.role != ChannelRole.OWNER &&
                member.role != ChannelRole.ADMIN)
        ) {
            throw ChannelFailure(
                "not_authorized",
                "Only owner or admin can rotate button secrets.",
            )
        }
        return buttons.rotateSecret(id, secretGenerator.generate())
    }
}
