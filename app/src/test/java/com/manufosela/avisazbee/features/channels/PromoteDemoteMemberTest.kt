package com.manufosela.avisazbee.features.channels

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.features.channels.domain.DemoteAdminToReceiverUseCase
import com.manufosela.avisazbee.features.channels.domain.PromoteMemberToAdminUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class PromoteDemoteMemberTest {

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
        uid = uid,
        role = role,
        joinedAt = now,
        notificationsEnabled = true,
    )

    @Test
    fun `promote moves a receiver to admin when caller is owner`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "target-uid") } returns
            member("target-uid", ChannelRole.RECEIVER)

        PromoteMemberToAdminUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            targetUid = "target-uid",
        )

        coVerify {
            repo.updateMemberRole("ch-1", "target-uid", ChannelRole.ADMIN)
        }
    }

    @Test
    fun `promote rejects a non-owner caller`() {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                PromoteMemberToAdminUseCase(repo)(
                    channelId = "ch-1",
                    callerUid = "admin-uid",
                    targetUid = "target-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("not_authorized")
    }

    @Test
    fun `promote is idempotent when target is already admin`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "target-uid") } returns
            member("target-uid", ChannelRole.ADMIN)

        PromoteMemberToAdminUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            targetUid = "target-uid",
        )

        coVerify(exactly = 0) {
            repo.updateMemberRole(any(), any(), any())
        }
    }

    @Test
    fun `demote moves an admin to receiver when caller is owner`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "admin-uid") } returns
            member("admin-uid", ChannelRole.ADMIN)

        DemoteAdminToReceiverUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            targetUid = "admin-uid",
        )

        coVerify {
            repo.updateMemberRole("ch-1", "admin-uid", ChannelRole.RECEIVER)
        }
    }

    @Test
    fun `demote refuses to touch the owner`() {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                DemoteAdminToReceiverUseCase(repo)(
                    channelId = "ch-1",
                    callerUid = "owner-uid",
                    targetUid = "owner-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("is_owner")
    }

    @Test
    fun `demote is idempotent on receivers`() = runTest {
        val repo: ChannelRepository = mockk(relaxUnitFun = true)
        coEvery { repo.findById("ch-1") } returns channel
        coEvery { repo.findMember("ch-1", "rcv-uid") } returns
            member("rcv-uid", ChannelRole.RECEIVER)

        DemoteAdminToReceiverUseCase(repo)(
            channelId = "ch-1",
            callerUid = "owner-uid",
            targetUid = "rcv-uid",
        )

        coVerify(exactly = 0) {
            repo.updateMemberRole(any(), any(), any())
        }
    }
}
