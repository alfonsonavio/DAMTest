package com.navio.damtests.core

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
 * [enabled] gates every vibration; it follows the "Vibración" setting.
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
            // Before Android 12 the vibrator is a system service of its own
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
    }

    /** Soft single tick for a correct answer. */
    fun correct() = vibrate(CORRECT_TIMINGS, CORRECT_AMPLITUDES)

    /** Stronger double buzz for a wrong answer. */
    fun wrong() = vibrate(WRONG_TIMINGS, WRONG_AMPLITUDES)

    /** Light tick for a neutral tap (e.g. moving to the next question). */
    fun tick() = vibrate(TICK_TIMINGS, TICK_AMPLITUDES)

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        // Some devices don't support amplitude control; fall back to timings only.
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(timings, NO_REPEAT)
        }
        vib.vibrate(effect)
    }

    private companion object {
        const val NO_REPEAT = -1

        // Waveforms: off/on durations (ms) and their strengths (0-255)
        val CORRECT_TIMINGS = longArrayOf(0, 40)
        val CORRECT_AMPLITUDES = intArrayOf(0, 120)
        val WRONG_TIMINGS = longArrayOf(0, 60, 80, 60)
        val WRONG_AMPLITUDES = intArrayOf(0, 200, 0, 200)
        val TICK_TIMINGS = longArrayOf(0, 20)
        val TICK_AMPLITUDES = intArrayOf(0, 80)
    }
}
