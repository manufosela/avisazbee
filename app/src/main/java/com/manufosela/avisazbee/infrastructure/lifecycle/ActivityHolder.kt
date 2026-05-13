package com.manufosela.avisazbee.infrastructure.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the topmost resumed Activity so non-UI components (e.g. the auth
 * repository invoking the Credentials API) can grab it without having
 * `android.app.Activity` leak into the domain layer.
 *
 * Registered as Application lifecycle callback in [com.manufosela.avisazbee.AvisazbeeApplication].
 */
@Singleton
class ActivityHolder @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var currentActivityRef: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) currentActivityRef = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
