package com.manufosela.avisazbee.shared

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class RandomTokenGeneratorTest {

    @Test
    fun `invite produces 6 chars from the unambiguous alphabet`() {
        val generator = RandomTokenGenerator.invite(random = Random(42))
        val pattern = Regex("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]+$")
        repeat(200) {
            val code = generator.generate()
            assertThat(code).hasLength(6)
            assertThat(pattern.matches(code)).isTrue()
        }
    }

    @Test
    fun `invite is deterministic given the same Random seed`() {
        val a = RandomTokenGenerator.invite(random = Random(7))
        val b = RandomTokenGenerator.invite(random = Random(7))
        assertThat(a.generate()).isEqualTo(b.generate())
    }

    @Test
    fun `secret produces 32 URL-safe chars`() {
        val generator = RandomTokenGenerator.secret(random = Random(0))
        val secret = generator.generate()
        assertThat(secret).hasLength(32)
        assertThat(secret.matches(Regex("^[A-Za-z0-9_\\-]+$"))).isTrue()
    }
}
