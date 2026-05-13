package com.manufosela.avisazbee.features.channels

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.features.channels.domain.JoinChannelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerify as coVerifyOnly
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant

class JoinChannelUseCaseTest {

    private val now = Instant.parse("2026-05-13T00:00:00Z")
    private lateinit var repository: ChannelRepository
    private lateinit var useCase: JoinChannelUseCase

    private fun channel(
        ownerUid: String = "owner-uid",
        inviteEnabled: Boolean = true,
    ) = Channel(
        id = "ch-1",
        name = "Casa",
        ownerUid = ownerUid,
        inviteCode = "ABCDEF",
        inviteEnabled = inviteEnabled,
        createdAt = now,
    )

    @Before
    fun setUp() {
        repository = mockk(relaxUnitFun = true)
        useCase = JoinChannelUseCase(repository)
    }

    @Test
    fun `adds the user as receiver when code is valid`() = runTest {
        val c = channel()
        coEvery { repository.findByInviteCode("ABCDEF") } returns c
        coEvery { repository.findMember("ch-1", "rcv-uid") } returns null

        val result = useCase(uid = "rcv-uid", inviteCode = "ABCDEF")

        assertThat(result).isEqualTo(c)
        coVerify {
            repository.addMember("ch-1", "rcv-uid", ChannelRole.RECEIVER)
        }
    }

    @Test
    fun `rejects unknown code`() {
        coEvery { repository.findByInviteCode(any()) } returns null
        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking { useCase(uid = "u", inviteCode = "ZZZZZZ") }
        }
        assertThat(ex.code).isEqualTo("invalid_code")
    }

    @Test
    fun `rejects when invites disabled`() {
        coEvery { repository.findByInviteCode("ABCDEF") } returns
            channel(inviteEnabled = false)
        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking { useCase(uid = "u", inviteCode = "ABCDEF") }
        }
        assertThat(ex.code).isEqualTo("invites_disabled")
    }

    @Test
    fun `rejects the owner trying to join their own channel`() {
        coEvery { repository.findByInviteCode("ABCDEF") } returns
            channel(ownerUid = "owner-uid")
        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking { useCase(uid = "owner-uid", inviteCode = "ABCDEF") }
        }
        assertThat(ex.code).isEqualTo("is_owner")
    }

    @Test
    fun `rejects an existing member`() {
        coEvery { repository.findByInviteCode("ABCDEF") } returns channel()
        coEvery { repository.findMember("ch-1", "rcv-uid") } returns
            ChannelMember(
                uid = "rcv-uid",
                role = ChannelRole.RECEIVER,
                joinedAt = now,
                notificationsEnabled = true,
            )

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking { useCase(uid = "rcv-uid", inviteCode = "ABCDEF") }
        }
        assertThat(ex.code).isEqualTo("already_member")
        coVerifyOnly(exactly = 0) {
            repository.addMember(any(), any(), any())
        }
    }
}
