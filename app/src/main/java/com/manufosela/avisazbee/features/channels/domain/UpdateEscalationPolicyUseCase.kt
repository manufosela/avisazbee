package com.manufosela.avisazbee.features.channels.domain

import javax.inject.Inject

/**
 * Validates and persists the escalation policy for a channel.
 *
 * Authorisation: only the channel owner or an admin can update the policy.
 *
 * Validation:
 *   - If `enabled`, the contact list must not be empty.
 *   - Every contact's phone matches a permissive E.164 shape `^\+?[0-9]{6,15}$`.
 *   - Each contact name is non-blank after trimming.
 */
class UpdateEscalationPolicyUseCase @Inject constructor(
    private val channels: ChannelRepository,
    private val policies: EscalationPolicyRepository,
) {
    suspend operator fun invoke(
        channelId: String,
        callerUid: String,
        policy: EscalationPolicy,
    ): EscalationPolicy {
        channels.findById(channelId)
            ?: throw ChannelFailure("not_found")
        val member = channels.findMember(channelId, callerUid)
        if (member == null ||
            (member.role != ChannelRole.OWNER &&
                member.role != ChannelRole.ADMIN)
        ) {
            throw ChannelFailure(
                "not_authorized",
                "Only owner or admin can update the escalation policy.",
            )
        }

        if (policy.enabled && policy.contacts.isEmpty()) {
            throw ChannelFailure(
                "empty_contacts",
                "Enabled escalation requires at least one contact.",
            )
        }
        for (contact in policy.contacts) {
            if (contact.name.trim().isEmpty()) {
                throw ChannelFailure(
                    "invalid_contact_name",
                    "Contact name cannot be empty.",
                )
            }
            if (!PHONE_PATTERN.matches(contact.phoneE164)) {
                throw ChannelFailure(
                    "invalid_phone",
                    "Invalid phone number: ${contact.phoneE164}",
                )
            }
        }

        policies.update(channelId, policy)
        return policy
    }

    private companion object {
        val PHONE_PATTERN = Regex("^\\+?[0-9]{6,15}$")
    }
}
