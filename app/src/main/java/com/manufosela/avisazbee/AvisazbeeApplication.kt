package com.manufosela.avisazbee

import android.app.Application
import com.manufosela.avisazbee.infrastructure.lifecycle.ActivityHolder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AvisazbeeApplication : Application() {

    @Inject lateinit var activityHolder: ActivityHolder

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityHolder)
    }
}
