package com.manufosela.avisazbee.infrastructure.di

import com.manufosela.avisazbee.shared.RandomTokenGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedModule {

    @Provides
    @Singleton
    @Named(INVITE_CODE_GENERATOR)
    fun provideInviteCodeGenerator(): RandomTokenGenerator =
        RandomTokenGenerator.invite()

    @Provides
    @Singleton
    @Named(SECRET_GENERATOR)
    fun provideSecretGenerator(): RandomTokenGenerator =
        RandomTokenGenerator.secret()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    const val INVITE_CODE_GENERATOR = "inviteCodeGenerator"
    const val SECRET_GENERATOR = "secretGenerator"
}
