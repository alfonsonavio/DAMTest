package com.navio.damtests.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * Centralised haptic feedback for the quiz.
 *
 * Distinguishes a correct answer (a single soft tick) from a wrong one (a
 * stronger double buzz), so the feedback is recognisable without looking.
 *
 * Designed to be toggleable: [enabled] gates every vibration. Once the settings
 * screen exists (planned with the UI redesign), wire a user preference to
 * [enabled] and no other code needs to change.
 */
class HapticFeedbackManager(
    private val context: Context,
    var enabled: Boolean = true
) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService<VibratorManager>()
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
    }

    /** Soft single tick for a correct answer. */
    fun correct() = vibrate(longArrayOf(0, 40), intArrayOf(0, 120))

    /** Stronger double buzz for a wrong answer. */
    fun wrong() = vibrate(longArrayOf(0, 60, 80, 60), intArrayOf(0, 200, 0, 200))

    /** Light tick for a neutral tap (e.g. moving to the next question). */
    fun tick() = vibrate(longArrayOf(0, 20), intArrayOf(0, 80))

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Some devices don't support amplitude control; fall back to timings only.
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings, -1)
        }
    }
}