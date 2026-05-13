package com.manufosela.avisazbee.features.devices

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.devices.domain.Device
import com.manufosela.avisazbee.features.devices.domain.DevicePlatform
import com.manufosela.avisazbee.features.devices.domain.DeviceRepository
import com.manufosela.avisazbee.features.devices.domain.RegisterDeviceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RegisterDeviceUseCaseTest {

    private val fixedNow: Instant = Instant.parse("2026-05-13T12:00:00Z")
    private val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private lateinit var repository: DeviceRepository
    private lateinit var useCase: RegisterDeviceUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxUnitFun = true)
        useCase = RegisterDeviceUseCase(repository, clock = fixedClock)
    }

    @Test
    fun `creates a new device when none exists`() = runTest {
        coEvery { repository.findById("dev-1") } returns null

        val result = useCase(
            deviceId = "dev-1",
            ownerUid = "uid-1",
            fcmToken = "token-abc",
            platform = DevicePlatform.ANDROID,
            name = "Pixel",
        )

        assertThat(result.id).isEqualTo("dev-1")
        assertThat(result.ownerUid).isEqualTo("uid-1")
        assertThat(result.fcmToken).isEqualTo("token-abc")
        assertThat(result.platform).isEqualTo(DevicePlatform.ANDROID)
        assertThat(result.name).isEqualTo("Pixel")
        assertThat(result.enabled).isTrue()
        assertThat(result.createdAt).isEqualTo(fixedNow)
        assertThat(result.updatedAt).isEqualTo(fixedNow)
        assertThat(result.subscribedChannels).isEmpty()
        coVerify { repository.upsert(result) }
    }

    @Test
    fun `preserves createdAt and subscribedChannels on update`() = runTest {
        val earlier = Instant.parse("2026-01-01T00:00:00Z")
        coEvery { repository.findById("dev-1") } returns Device(
            id = "dev-1",
            ownerUid = "uid-1",
            fcmToken = "old-token",
            platform = DevicePlatform.ANDROID,
            name = "Old",
            enabled = true,
            createdAt = earlier,
            updatedAt = earlier,
            subscribedChannels = listOf("ch-1", "ch-2"),
        )
        val captured = slot<Device>()
        coEvery { repository.upsert(capture(captured)) } returns Unit

        val result = useCase(
            deviceId = "dev-1",
            ownerUid = "uid-1",
            fcmToken = "new-token",
            platform = DevicePlatform.ANDROID,
            name = "New",
        )

        assertThat(result.createdAt).isEqualTo(earlier)
        assertThat(result.updatedAt).isEqualTo(fixedNow)
        assertThat(result.fcmToken).isEqualTo("new-token")
        assertThat(result.name).isEqualTo("New")
        assertThat(result.subscribedChannels).containsExactly("ch-1", "ch-2")
            .inOrder()
        assertThat(captured.captured).isEqualTo(result)
    }
}
