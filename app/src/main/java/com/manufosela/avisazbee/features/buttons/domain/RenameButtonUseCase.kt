package com.manufosela.avisazbee.features.buttons.domain

import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import javax.inject.Inject

/** Renames a button. Authorised for owner/admin of the button's channel. */
class RenameButtonUseCase @Inject constructor(
    private val buttons: ButtonRepository,
    private val channels: ChannelRepository,
) {
    suspend operator fun invoke(
        buttonId: String,
        callerUid: String,
        newName: String,
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            throw ButtonFailure(
                "invalid_button_name",
                "Button name is required.",
            )
        }
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
                "Only owner or admin can rename buttons.",
            )
        }
        if (button.name == trimmed) return
        buttons.rename(id, trimmed)
    }
}
