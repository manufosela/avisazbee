package com.manufosela.avisazbee.shared

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Generates random tokens from a configurable alphabet.
 *
 * Two presets are exposed for the domain:
 *   - [invite]: short, human-readable invite codes.
 *   - [secret]: long, URL-safe secrets for backend use.
 *
 * The [Random] source can be injected for deterministic tests.
 */
class RandomTokenGenerator(
    private val alphabet: String,
    private val length: Int,
    private val random: Random = secureRandom(),
) {
    init {
        require(alphabet.length > 1) { "Alphabet too short" }
        require(length > 0) { "Length must be positive" }
    }

    fun generate(): String {
        // Local copy avoids accidentally referring to StringBuilder.length
        // inside the builder lambda.
        val size = length
        return buildString(size) {
            repeat(size) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        /** Codes humans must read aloud and type back. No 0/O/1/I. */
        fun invite(random: Random = secureRandom()): RandomTokenGenerator =
            RandomTokenGenerator(
                alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
                length = 6,
                random = random,
            )

        /** Long, URL-safe secret for machine-to-machine integrations. */
        fun secret(random: Random = secureRandom()): RandomTokenGenerator =
            RandomTokenGenerator(
                alphabet =
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_",
                length = 32,
                random = random,
            )

        private fun secureRandom(): Random = SecureRandom().asKotlinRandom()
    }
}
