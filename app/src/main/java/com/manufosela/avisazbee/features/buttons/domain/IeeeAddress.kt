package com.manufosela.avisazbee.features.buttons.domain

/**
 * Validates and normalises a Zigbee IEEE MAC address.
 *
 * Accepts both colon and hyphen separators and any letter case; normalises
 * to upper-case colon form, e.g. `E4:56:AC:FF:FE:5E:CD:AA`.
 *
 * Throws [ButtonFailure] with code `invalid_button_id` when the input does
 * not contain exactly eight pairs of hex digits.
 */
internal object IeeeAddress {
    private val PATTERN = Regex("^([0-9A-Fa-f]{2}[:\\-]){7}[0-9A-Fa-f]{2}$")

    fun normalise(raw: String): String {
        val trimmed = raw.trim()
        if (!PATTERN.matches(trimmed)) {
            throw ButtonFailure(
                "invalid_button_id",
                "Expected 8 hex octets separated by ':' or '-'; got '$raw'.",
            )
        }
        return trimmed.uppercase().replace('-', ':')
    }
}
