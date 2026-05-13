package com.manufosela.avisazbee.features.buttons

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.buttons.domain.ButtonFailure
import com.manufosela.avisazbee.features.buttons.domain.IeeeAddress
import org.junit.Test
import org.junit.Assert.assertThrows

class IeeeAddressTest {

    @Test
    fun `normalise upper-cases and uses colon separators`() {
        val out = IeeeAddress.normalise("e4:56:ac:ff:fe:5e:cd:aa")
        assertThat(out).isEqualTo("E4:56:AC:FF:FE:5E:CD:AA")
    }

    @Test
    fun `normalise accepts hyphen separators`() {
        val out = IeeeAddress.normalise("E4-56-AC-FF-FE-5E-CD-AA")
        assertThat(out).isEqualTo("E4:56:AC:FF:FE:5E:CD:AA")
    }

    @Test
    fun `normalise trims surrounding whitespace`() {
        val out = IeeeAddress.normalise("  E4:56:AC:FF:FE:5E:CD:AA  ")
        assertThat(out).isEqualTo("E4:56:AC:FF:FE:5E:CD:AA")
    }

    @Test
    fun `normalise rejects wrong octet count`() {
        val ex = assertThrows(ButtonFailure::class.java) {
            IeeeAddress.normalise("E4:56:AC:FF:FE:5E:CD")
        }
        assertThat(ex.code).isEqualTo("invalid_button_id")
    }

    @Test
    fun `normalise rejects non-hex characters`() {
        val ex = assertThrows(ButtonFailure::class.java) {
            IeeeAddress.normalise("ZZ:56:AC:FF:FE:5E:CD:AA")
        }
        assertThat(ex.code).isEqualTo("invalid_button_id")
    }
}
