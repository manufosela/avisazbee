package com.manufosela.avisazbee.features.channels

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.CreateChannelUseCase
import com.manufosela.avisazbee.shared.RandomTokenGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class CreateChannelUseCaseTest {

    private val repository: ChannelRepository = mockk()

    @Test
    fun `generates an invite code and forwards a trimmed name`() = runTest {
        val useCase = CreateChannelUseCase(
            repository = repository,
            inviteCodeGenerator = RandomTokenGenerator.invite(random = Random(1)),
        )
        val channel = Channel(
            id = "ch-1",
            name = "Casa Manu",
            ownerUid = "uid-1",
            inviteCode = "CODE01",
            inviteEnabled = true,
            createdAt = Instant.parse("2026-05-13T00:00:00Z"),
        )
        val codeSlot = slot<String>()
        coEvery {
            repository.createChannel(
                name = "Casa Manu",
                ownerUid = "uid-1",
                inviteCode = capture(codeSlot),
            )
        } returns channel

        val result = useCase(name = "  Casa Manu  ", ownerUid = "uid-1")

        assertThat(result).isEqualTo(channel)
        assertThat(codeSlot.captured).hasLength(6)
        coVerify {
            repository.createChannel(
                name = "Casa Manu",
                ownerUid = "uid-1",
                inviteCode = any(),
            )
        }
    }

    @Test
    fun `rejects an empty name`() {
        val useCase = CreateChannelUseCase(repository)
        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking { useCase(name = "   ", ownerUid = "uid-1") }
        }
        assertThat(ex.code).isEqualTo("invalid_name")
    }
}
