package com.manufosela.avisazbee.features.channels.domain

import java.time.Instant

/**
 * A channel: one logical point of emission with one owner, optional admins
 * and any number of receivers. Buttons (Zigbee or otherwise) point to a
 * channel via `Button.channelId`.
 */
data class Channel(
    val id: String,
    val name: String,
    val ownerUid: String,
    /** Short, human-readable code receivers introduce in the app to join. */
    val inviteCode: String,
    /** When `false`, the invite code does not grant access. */
    val inviteEnabled: Boolean,
    val createdAt: Instant,
)
