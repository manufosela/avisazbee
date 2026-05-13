package com.manufosela.avisazbee.features.channels.domain

import java.time.Instant

/**
 * Role a user plays in a channel.
 *
 *   - [OWNER]: creator. Sole role that can delete the channel, promote and
 *     demote admins and transfer ownership.
 *   - [ADMIN]: delegated co-manager. Can edit the escalation policy, add
 *     and remove receivers, manage buttons and dialers. Cannot touch other
 *     admins nor delete the channel.
 *   - [RECEIVER]: receives alerts and can claim / resolve them. No editing
 *     rights.
 */
enum class ChannelRole { OWNER, ADMIN, RECEIVER }

data class ChannelMember(
    val uid: String,
    val role: ChannelRole,
    val joinedAt: Instant,
    val notificationsEnabled: Boolean,
)
