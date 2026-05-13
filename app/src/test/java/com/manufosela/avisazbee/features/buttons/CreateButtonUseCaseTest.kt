package com.manufosela.avisazbee.features.buttons

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.buttons.domain.Button
import com.manufosela.avisazbee.features.buttons.domain.ButtonFailure
import com.manufosela.avisazbee.features.buttons.domain.ButtonRepository
import com.manufosela.avisazbee.features.buttons.domain.CreateButtonUseCase
import com.manufosela.avisazbee.features.buttons.domain.NewButton
import com.manufosela.avisazbee.features.channels.domain.Channel
import com.manufosela.avisazbee.features.channels.domain.ChannelFailure
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import com.manufosela.avisazbee.shared.RandomTokenGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class CreateButtonUseCaseTest {

    private lateinit var buttons: ButtonRepository
    private lateinit var channels: ChannelRepository
    private lateinit var useCase: CreateButtonUseCase

    private val now = Instant.parse("2026-05-13T12:00:00Z")
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

    @Before
    fun setUp() {
        buttons = mockk(relaxUnitFun = true)
        channels = mockk()
        useCase = CreateButtonUseCase(
            buttons = buttons,
            channels = channels,
            secretGenerator = RandomTokenGenerator.secret(random = Random(0)),
        )
    }

    @Test
    fun `creates a button when caller is owner`() = runTest {
        coEvery { channels.findById("ch-1") } returns channel
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)
        coEvery { buttons.findById(any()) } returns null
        val created = NewButton(
            metadata = Button(
                id = "E4:56:AC:FF:FE:5E:CD:AA",
                name = "Botón cocina",
                channelId = "ch-1",
                enabled = true,
                createdAt = now,
                createdBy = "owner-uid",
                hashedSecret = "<hash>",
            ),
            plainSecret = "<plain-once>",
        )
        coEvery {
            buttons.create(
                id = "E4:56:AC:FF:FE:5E:CD:AA",
                name = "Botón cocina",
                channelId = "ch-1",
                createdBy = "owner-uid",
                plainSecret = any(),
            )
        } returns created

        val result = useCase(
            rawButtonId = "e4:56:ac:ff:fe:5e:cd:aa",
            name = "  Botón cocina  ",
            channelId = "ch-1",
            callerUid = "owner-uid",
        )

        assertThat(result).isEqualTo(created)
        coVerify {
            buttons.create(
                id = "E4:56:AC:FF:FE:5E:CD:AA",
                name = "Botón cocina",
                channelId = "ch-1",
                createdBy = "owner-uid",
                plainSecret = any(),
            )
        }
    }

    @Test
    fun `also accepts an admin caller`() = runTest {
        coEvery { channels.findById("ch-1") } returns channel
        coEvery { channels.findMember("ch-1", "admin-uid") } returns
            member("admin-uid", ChannelRole.ADMIN)
        coEvery { buttons.findById(any()) } returns null
        coEvery {
            buttons.create(any(), any(), any(), any(), any())
        } returns NewButton(
            metadata = Button(
                id = "E4:56:AC:FF:FE:5E:CD:AA",
                name = "B",
                channelId = "ch-1",
                enabled = true,
                createdAt = now,
                createdBy = "admin-uid",
                hashedSecret = "<hash>",
            ),
            plainSecret = "<plain>",
        )

        useCase(
            rawButtonId = "E4:56:AC:FF:FE:5E:CD:AA",
            name = "B",
            channelId = "ch-1",
            callerUid = "admin-uid",
        )
    }

    @Test
    fun `rejects a receiver caller`() = runTest {
        coEvery { channels.findById("ch-1") } returns channel
        coEvery { channels.findMember("ch-1", "rcv-uid") } returns
            member("rcv-uid", ChannelRole.RECEIVER)

        val ex = assertThrows(ChannelFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    rawButtonId = "E4:56:AC:FF:FE:5E:CD:AA",
                    name = "B",
                    channelId = "ch-1",
                    callerUid = "rcv-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("not_authorized")
    }

    @Test
    fun `rejects empty name`() = runTest {
        val ex = assertThrows(ButtonFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    rawButtonId = "E4:56:AC:FF:FE:5E:CD:AA",
                    name = "   ",
                    channelId = "ch-1",
                    callerUid = "owner-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("invalid_button_name")
    }

    @Test
    fun `rejects malformed button id`() = runTest {
        val ex = assertThrows(ButtonFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    rawButtonId = "not-a-mac",
                    name = "B",
                    channelId = "ch-1",
                    callerUid = "owner-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("invalid_button_id")
    }

    @Test
    fun `rejects when a button with that id already exists`() = runTest {
        coEvery { channels.findById("ch-1") } returns channel
        coEvery { channels.findMember("ch-1", "owner-uid") } returns
            member("owner-uid", ChannelRole.OWNER)
        coEvery { buttons.findById("E4:56:AC:FF:FE:5E:CD:AA") } returns Button(
            id = "E4:56:AC:FF:FE:5E:CD:AA",
            name = "Old",
            channelId = "ch-1",
            enabled = true,
            createdAt = now,
            createdBy = "owner-uid",
            hashedSecret = "h",
        )

        val ex = assertThrows(ButtonFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    rawButtonId = "E4:56:AC:FF:FE:5E:CD:AA",
                    name = "B",
                    channelId = "ch-1",
                    callerUid = "owner-uid",
                )
            }
        }
        assertThat(ex.code).isEqualTo("already_exists")
    }
}
