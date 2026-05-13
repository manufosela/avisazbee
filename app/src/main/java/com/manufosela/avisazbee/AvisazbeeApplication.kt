package com.manufosela.avisazbee

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.manufosela.avisazbee.infrastructure.lifecycle.ActivityHolder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AvisazbeeApplication : Application(), Configuration.Provider {

    @Inject lateinit var activityHolder: ActivityHolder
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityHolder)
    }
}
