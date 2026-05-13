package com.manufosela.avisazbee.features.users

import com.google.common.truth.Truth.assertThat
import com.manufosela.avisazbee.features.users.domain.NotificationMode
import com.manufosela.avisazbee.features.users.domain.NotificationPreferences
import com.manufosela.avisazbee.features.users.domain.UpdateNotificationModeUseCase
import com.manufosela.avisazbee.features.users.domain.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpdateNotificationModeUseCaseTest {

    @Test
    fun `reads current, overrides the mode and persists`() = runTest {
        val repo: UserPreferencesRepository = mockk(relaxUnitFun = true)
        coEvery { repo.getFor("uid-1") } returns NotificationPreferences.Defaults
        val captured = slot<NotificationPreferences>()
        coEvery { repo.update("uid-1", capture(captured)) } returns Unit

        val result = UpdateNotificationModeUseCase(repo)(
            uid = "uid-1",
            mode = NotificationMode.SILENT,
        )

        assertThat(result.mode).isEqualTo(NotificationMode.SILENT)
        assertThat(captured.captured)
            .isEqualTo(NotificationPreferences(NotificationMode.SILENT))
        coVerify { repo.update("uid-1", any()) }
    }

    @Test
    fun `defaults to BOTH for brand new users`() = runTest {
        val repo: UserPreferencesRepository = mockk(relaxUnitFun = true)
        coEvery { repo.getFor("uid-new") } returns NotificationPreferences.Defaults
        val initial = repo.getFor("uid-new")
        assertThat(initial.mode).isEqualTo(NotificationMode.BOTH)
    }
}
