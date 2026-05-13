package com.manufosela.avisazbee

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the application component here.
 *
 * Phase 0 keeps this minimal. Subsequent phases will:
 *   - initialise Firebase explicitly (currently it auto-inits via the
 *     `google-services.json` content provider),
 *   - register the FCM service,
 *   - schedule the device pulse-of-life work.
 */
@HiltAndroidApp
class AvisazbeeApplication : Application()
