package com.manufosela.avisazbee.features.buttons.domain

import java.time.Instant

/**
 * Identity of a physical emitter (a Zigbee button via Home Assistant in the
 * current scope). One button belongs to exactly one channel; that channel
 * decides who receives the alerts the button produces.
 *
 * The button id is the Zigbee IEEE MAC of the device, e.g.
 * `E4:56:AC:FF:FE:5E:CD:AA` — globally unique by manufacturer, so we use it
 * directly as the Firestore document id (no extra mapping).
 *
 * The plain HTTP secret used by Home Assistant to authenticate calls is
 * never stored or exposed; only its bcrypt hash lives in [hashedSecret].
 */
data class Button(
    val id: String,
    val name: String,
    val channelId: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val createdBy: String,
    /** bcrypt hash of the plain HTTP secret used by Home Assistant. */
    val hashedSecret: String,
)

/**
 * Carries the freshly generated plain secret of a newly created or rotated
 * button. The UI must show it once to the user (so they can copy it into
 * Home Assistant) and discard it; it is never persisted client-side.
 */
data class NewButton(
    val metadata: Button,
    val plainSecret: String,
)
