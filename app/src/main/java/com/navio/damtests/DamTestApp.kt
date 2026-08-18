package com.navio.damtests

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class required by Hilt to generate the dependency container.
 * Registered in AndroidManifest via android:name=".DamTestApp".
 */
@HiltAndroidApp
class DamTestApp : Application()