package com.manufosela.avisazbee.features.buttons.domain

import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.shared.RandomTokenGenerator
import javax.inject.Inject
import javax.inject.Named

/**
 * Registers a Zigbee button against a channel.
 *
 *   - Validates and normalises the IEEE MAC.
 *   - Requires a non-blank human-readable name.
 *   - Authorises only owner/admin of the target channel.
 *   - Generates a fresh HTTP secret and returns it once via [NewButton];
 *     the repository implementation stores only its hash.
 */
class CreateButtonUseCase @Inject constructor(
    private val buttons: ButtonRepository,
    private val channels: ChannelRepository,
    @Named("secretGenerator")
    private val secretGenerator: RandomTokenGenerator = RandomTokenGenerator.secret(),
) {
    suspend operator fun invoke(
        rawButtonId: String,
        name: String,
        channelId: String,
        callerUid: String,
    ): NewButton {
        val id = IeeeAddress.normalise(rawButtonId)
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw ButtonFailure(
                "invalid_button_name",
                "Button name is required.",
            )
        }

        channels.findById(channelId)
            ?: throw ChannelFailure("not_found")
        val member = channels.findMember(channelId, callerUid)
        if (member == null ||
            (member.role != ChannelRole.OWNER &&
                member.role != ChannelRole.ADMIN)
        ) {
            throw ChannelFailure(
                "not_authorized",
                "Only owner or admin can register buttons.",
            )
        }

        val existing = buttons.findById(id)
        if (existing != null) {
            throw ButtonFailure(
                "already_exists",
                "A button with id '$id' is already registered.",
            )
        }

        return buttons.create(
            id = id,
            name = trimmedName,
            channelId = channelId,
            createdBy = callerUid,
            plainSecret = secretGenerator.generate(),
        )
    }
}
