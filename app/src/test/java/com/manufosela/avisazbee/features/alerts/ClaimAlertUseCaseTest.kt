package com.manufosela.avisazbee.features.alerts

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.alerts.domain.Alert
import com.manufosela.avisazbee.features.alerts.domain.AlertFailure
import com.manufosela.avisazbee.features.alerts.domain.AlertRepository
import com.manufosela.avisazbee.features.alerts.domain.AlertStatus
import com.manufosela.avisazbee.features.alerts.domain.ClaimAlertUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ClaimAlertUseCaseTest {

    private val now = Instant.parse("2026-05-13T00:00:00Z")
    private val claimed = Alert(
        id = "alert-1",
        channelId = "ch-1",
        buttonId = "E4:56:AC:FF:FE:5E:CD:AA",
        buttonName = "Botón cocina",
        type = "zigbee_button",
        title = "Aviso",
        message = "Se ha pulsado el botón",
        source = "home_assistant",
        createdAt = now,
        status = AlertStatus.CLAIMED,
        claimedBy = "uid-1",
        claimedAt = now.plusSeconds(5),
    )

    @Test
    fun `returns the claimed alert from the repository`() = runTest {
        val repo: AlertRepository = mockk()
        coEvery { repo.claim("alert-1", "uid-1") } returns claimed

        val result = ClaimAlertUseCase(repo)("alert-1", "uid-1")

        assertThat(result).isEqualTo(claimed)
        assertThat(result.isClaimed).isTrue()
        coVerify { repo.claim("alert-1", "uid-1") }
    }

    @Test
    fun `surfaces AlertFailure(already_claimed)`() {
        val repo: AlertRepository = mockk()
        coEvery { repo.claim("alert-1", "uid-2") } throws
            AlertFailure("already_claimed")

        val ex = assertThrows(AlertFailure::class.java) {
            runBlocking { ClaimAlertUseCase(repo)("alert-1", "uid-2") }
        }
        assertThat(ex.code).isEqualTo("already_claimed")
    }
}
