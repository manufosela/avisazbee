package com.manufosela.avisazbee.features.buttons.domain

import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import javax.inject.Inject

/**
 * Enables or disables a button. A disabled button does not produce alerts —
 * the Cloud Function rejects HTTP calls referencing it.
 */
class SetButtonEnabledUseCase @Inject constructor(
    private val buttons: ButtonRepository,
    private val channels: ChannelRepository,
) {
    suspend operator fun invoke(
        buttonId: String,
        callerUid: String,
        enabled: Boolean,
    ) {
        val id = IeeeAddress.normalise(buttonId)
        val button = buttons.findById(id)
            ?: throw ButtonFailure("not_found")
        assertCallerCanManage(button.channelId, callerUid)
        if (button.enabled == enabled) return
        buttons.setEnabled(id, enabled)
    }

    private suspend fun assertCallerCanManage(
        channelId: String,
        callerUid: String,
    ) {
        val member = channels.findMember(channelId, callerUid)
        if (member == null ||
            (member.role != ChannelRole.OWNER &&
                member.role != ChannelRole.ADMIN)
        ) {
            throw ChannelFailure(
                "not_authorized",
                "Only owner or admin can manage buttons.",
            )
        }
    }
}
