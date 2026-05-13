package com.manufosela.avisazbee.features.channels

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.channels.domain.AddDialerUseCase
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelDialer
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.features.channels.domain.RemoveDialerUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class DialerUseCasesTest {

    private val now = Instant.parse("2026-05-13T00:00:00Z")
    private val channel = Channel(
        id = "ch-1",
        name = "Casa",
        ownerUid = "owner-uid",
        inviteCode = "ABCDEF",
        inviteEnabled = true,
        createdAt = now,
    )

    private fun member(uid: String, role: ChannelRole) = ChannelMember(
        uid = uid, role = role, joinedAt = now, notificationsEnabled = true,
    )

    @Test
    fun `add - admin marks a new device as dialer`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "admin-uid") } returns
            member("admin-uid", ChannelRole.ADMIN)
        coEvery { repo.findDialer("ch-1", "dev-1") } returns null

        AddDialerUseCase(repo)(
            channelId = "ch-1",
            callerUid = "admin-uid",
            deviceId = "dev-1",
        )

        coVerify {
            repo.addDialer("ch-1", "dev-1", addedBy = "admin-uid")
        }
    }

    @Test
    fun `add - idempotent when dialer exists`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)
        coEvery { repo.findDialer("ch-1", "dev-1") } returns ChannelDialer(
            channelId = "ch-1",
            deviceId = "dev-1",
            addedBy = "x",
            addedAt = now,
        )

        AddDialerUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            deviceId = "dev-1",
        )

        coVerify(exactly = 0) {
            repo.addDialer(any(), any(), any())
        }
    }

    @Test
    fun `add - rejects a receiver caller`() {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "rcv-uid") } returns
            member("rcv-uid", ChannelRole.RECEIVER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                AddDialerUseCase(repo)(
                    channelId = "ch-1",
                    callerUid = "rcv-uid",
                    deviceId = "dev-1",
                )
            }
        }
        assertThat(ex.code).isEqualTo("not_authorized")
    }

    @Test
    fun `remove - owner can remove a dialer`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)

        RemoveDialerUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            deviceId = "dev-1",
        )

        coVerify { repo.removeDialer("ch-1", "dev-1") }
    }

    @Test
    fun `remove - rejects a receiver caller`() {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "rcv-uid") } returns
            member("rcv-uid", ChannelRole.RECEIVER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                RemoveDialerUseCase(repo)(
                    channelId = "ch-1",
                    callerUid = "rcv-uid",
                    deviceId = "dev-1",
                )
            }
        }
        assertThat(ex.code).isEqualTo("not_authorized")
    }
}
