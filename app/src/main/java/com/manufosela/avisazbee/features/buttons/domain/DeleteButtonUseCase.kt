package com.manufosela.avisazbee.features.buttons.domain

import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import javax.inject.Inject

/**
 * Removes a button. After deletion, any pending HA request quoting this id
 * is rejected. The historical alerts created by this button stay in
 * Firestore unchanged (they reference id and name by value).
 */
class DeleteButtonUseCase @Inject constructor(
    private val buttons: ButtonRepository,
    private val channels: ChannelRepository,
) {
    suspend operator fun invoke(buttonId: String, callerUid: String) {
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
                "Only owner or admin can delete buttons.",
            )
        }
        buttons.delete(id)
    }
}
