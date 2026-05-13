package com.manufosela.avisazbee.infrastructure.escalation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EscalationModule {

    @Binds
    @Singleton
    abstract fun bindContactDialer(impl: TelephonyContactDialer): ContactDialer
}
