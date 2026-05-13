package com.manufosela.avisazbee.features.channels

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.features.channels.domain.EscalationContact
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicy
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicyRepository
import com.manufosela.avisazbee.features.channels.domain.UpdateEscalationPolicyUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant

class UpdateEscalationPolicyUseCaseTest {

    private val now = Instant.parse("2026-05-13T00:00:00Z")
    private val channel = Channel(
        id = "ch-1",
        name = "Casa",
        ownerUid = "owner-uid",
        inviteCode = "ABCDEF",
        inviteEnabled = true,
        createdAt = now,
    )

    private lateinit var channels: ChannelRepository
    private lateinit var policies: EscalationPolicyRepository
    private lateinit var useCase: UpdateEscalationPolicyUseCase

    private fun member(uid: String, role: ChannelRole) = ChannelMember(
        uid = uid, role = role, joinedAt = now, notificationsEnabled = true,
    )

    @Before
    fun setUp() {
        channels = mockk()
        policies = mockk(relaxUnitFun = true)
        useCase = UpdateEscalationPolicyUseCase(channels, policies)
        coEvery { channels.findById("ch-1") } returns channel
    }

    @Test
    fun `persists when caller is owner and payload is valid`() = runTest {
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)

        val policy = EscalationPolicy(
            enabled = true,
            contacts = listOf(
                EscalationContact("María", "+34600111222"),
                EscalationContact("Pedro", "+34600333444"),
            ),
        )

        val result = useCase("ch-1", "owner-uid", policy)
        assertThat(result).isEqualTo(policy)
        coVerify { policies.update("ch-1", policy) }
    }

    @Test
    fun `also accepts an admin caller`() = runTest {
        coEvery { channels.findMember("ch-1", "admin-uid") } returns
            member("admin-uid", ChannelRole.ADMIN)

        val policy = EscalationPolicy(
            enabled = true,
            contacts = listOf(EscalationContact("A", "+34600000000")),
        )

        useCase("ch-1", "admin-uid", policy)
        coVerify { policies.update("ch-1", policy) }
    }

    @Test
    fun `rejects a receiver caller`() {
        coEvery { channels.findMember("ch-1", "rcv-uid") } returns
            member("rcv-uid", ChannelRole.RECEIVER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                useCase("ch-1", "rcv-uid", EscalationPolicy.Disabled)
            }
        }
        assertThat(ex.code).isEqualTo("not_authorized")
    }

    @Test
    fun `rejects enabled policy with empty contacts`() {
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                useCase(
                    "ch-1",
                    "owner-uid",
                    EscalationPolicy(enabled = true, contacts = emptyList()),
                )
            }
        }
        assertThat(ex.code).isEqualTo("empty_contacts")
    }

    @Test
    fun `rejects invalid phone numbers`() {
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                useCase(
                    "ch-1",
                    "owner-uid",
                    EscalationPolicy(
                        enabled = true,
                        contacts = listOf(
                            EscalationContact("María", "no-es-un-tel"),
                        ),
                    ),
                )
            }
        }
        assertThat(ex.code).isEqualTo("invalid_phone")
    }

    @Test
    fun `rejects empty contact names`() {
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)

        val ex = assertThrows(ChannelFailure::class.java) {
            runBlocking {
                useCase(
                    "ch-1",
                    "owner-uid",
                    EscalationPolicy(
                        enabled = true,
                        contacts = listOf(
                            EscalationContact("  ", "+34600000000"),
                        ),
                    ),
                )
            }
        }
        assertThat(ex.code).isEqualTo("invalid_contact_name")
    }
}
