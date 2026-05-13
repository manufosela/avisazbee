package com.manufosela.avisazbee.features.channels.domain

/**
 * A single phone number the dialer should call during an escalation,
 * together with a human-friendly name for the UI.
 */
data class EscalationContact(
    /** Display name (e.g. "María — hija"). */
    val name: String,
    /** Phone in E.164 format (`+34600123456`). Validation belongs in
     *  [UpdateEscalationPolicyUseCase], not in the model. */
    val phoneE164: String,
)

/**
 * Per-channel auto-escalation configuration.
 *
 * When [enabled] is true, the dialer device of the channel starts placing
 * calls if an alert stays in `pending` for [escalateAfterSeconds]. The
 * dialer cycles through [contacts] in order, ringing each for
 * [perContactRingSeconds]. If [loopUntilAnswered] is true the dialer loops
 * forever until somebody answers or the device runs out of battery —
 * explicit decision by product.
 */
data class EscalationPolicy(
    val enabled: Boolean = true,
    val escalateAfterSeconds: Int = 180,
    val contacts: List<EscalationContact> = emptyList(),
    val perContactRingSeconds: Int = 25,
    val useSpeakerphone: Boolean = true,
    val loopUntilAnswered: Boolean = true,
) {
    init {
        require(escalateAfterSeconds >= 30) {
            "escalateAfterSeconds must be >= 30"
        }
        require(perContactRingSeconds in 10..90) {
            "perContactRingSeconds must be within [10, 90]"
        }
    }

    companion object {
        /** "No escalation" preset for brand new channels. */
        val Disabled: EscalationPolicy = EscalationPolicy(
            enabled = false,
            contacts = emptyList(),
        )
    }
}
