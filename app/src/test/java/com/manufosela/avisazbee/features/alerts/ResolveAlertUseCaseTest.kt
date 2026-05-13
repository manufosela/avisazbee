package com.manufosela.avisazbee.features.alerts

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.alerts.domain.Alert
import com.manufosela.avisazbee.features.alerts.domain.AlertFailure
import com.manufosela.avisazbee.features.alerts.domain.AlertRepository
import com.manufosela.avisazbee.features.alerts.domain.AlertStatus
import com.manufosela.avisazbee.features.alerts.domain.ResolveAlertUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ResolveAlertUseCaseTest {

    private val now = Instant.parse("2026-05-13T00:00:00Z")
    private fun resolved(resolution: String) = Alert(
        id = "alert-1",
        channelId = "ch-1",
        buttonId = "E4:56:AC:FF:FE:5E:CD:AA",
        buttonName = "Botón cocina",
        type = "zigbee_button",
        title = "Aviso",
        message = "Se ha pulsado el botón",
        source = "home_assistant",
        createdAt = now,
        status = AlertStatus.RESOLVED,
        resolvedBy = "uid-1",
        resolvedAt = now.plusSeconds(60),
        resolution = resolution,
    )

    @Test
    fun `trims the message and forwards it`() = runTest {
        val repo: AlertRepository = mockk()
        val expected = resolved("Todo OK, falsa alarma")
        coEvery {
            repo.resolve("alert-1", "uid-1", "Todo OK, falsa alarma")
        } returns expected

        val result = ResolveAlertUseCase(repo)(
            alertId = "alert-1",
            uid = "uid-1",
            resolution = "  Todo OK, falsa alarma  ",
        )

        assertThat(result.isResolved).isTrue()
        assertThat(result.resolution).isEqualTo("Todo OK, falsa alarma")
        coVerify {
            repo.resolve("alert-1", "uid-1", "Todo OK, falsa alarma")
        }
    }

    @Test
    fun `rejects empty resolution`() {
        val repo: AlertRepository = mockk()
        val ex = assertThrows(AlertFailure::class.java) {
            runBlocking {
                ResolveAlertUseCase(repo)(
                    alertId = "alert-1",
                    uid = "uid-1",
                    resolution = "   ",
                )
            }
        }
        assertThat(ex.code).isEqualTo("invalid_resolution")
        coVerify(exactly = 0) { repo.resolve(any(), any(), any()) }
    }

    @Test
    fun `surfaces already_resolved from repository`() {
        val repo: AlertRepository = mockk()
        coEvery { repo.resolve("alert-1", "uid-1", "OK") } throws
            AlertFailure("already_resolved")
        val ex = assertThrows(AlertFailure::class.java) {
            runBlocking {
                ResolveAlertUseCase(repo)(
                    alertId = "alert-1",
                    uid = "uid-1",
                    resolution = "OK",
                )
            }
        }
        assertThat(ex.code).isEqualTo("already_resolved")
    }
}
