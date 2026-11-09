package com.navio.damtests.data.exam

import android.os.SystemClock
import javax.inject.Inject

/**
 * Time source of the exam timer. Uses the time since boot, which keeps
 * counting while the phone sleeps and can't be changed from the settings,
 * so leaving the app or changing the clock never gives extra time.
 */
open class ExamClock @Inject constructor() {
    open fun now(): Long = SystemClock.elapsedRealtime()
}