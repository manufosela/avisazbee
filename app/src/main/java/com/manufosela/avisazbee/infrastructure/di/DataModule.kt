package com.manufosela.avisazbee.infrastructure.di

import com.manufosela.avisazbee.features.alerts.data.FirestoreAlertRepository
import com.manufosela.avisazbee.features.alerts.domain.AlertRepository
import com.manufosela.avisazbee.features.auth.domain.AuthRepository
import com.manufosela.avisazbee.features.buttons.data.FirestoreButtonRepository
import com.manufosela.avisazbee.features.buttons.domain.ButtonRepository
import com.manufosela.avisazbee.features.channels.data.FirestoreChannelRepository
import com.manufosela.avisazbee.features.channels.data.FirestoreEscalationPolicyRepository
import com.manufosela.avisazbee.features.channels.domain.ChannelRepository
import com.manufosela.avisazbee.features.channels.domain.EscalationPolicyRepository
import com.manufosela.avisazbee.features.devices.data.FirestoreDeviceRepository
import com.manufosela.avisazbee.features.devices.domain.DeviceRepository
import com.manufosela.avisazbee.features.users.data.FirestoreUserPreferencesRepository
import com.manufosela.avisazbee.features.users.domain.UserPreferencesRepository
import com.manufosela.avisazbee.infrastructure.firebase.auth.FirebaseAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: FirestoreChannelRepository): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindEscalationPolicyRepository(
        impl: FirestoreEscalationPolicyRepository,
    ): EscalationPolicyRepository

    @Binds
    @Singleton
    abstract fun bindButtonRepository(impl: FirestoreButtonRepository): ButtonRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: FirestoreAlertRepository): AlertRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: FirestoreDeviceRepository): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: FirestoreUserPreferencesRepository,
    ): UserPreferencesRepository
}
