package dev.betterwork.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import dev.betterwork.domain.Cue

class Cues(context: Context) {
    @Suppress("DEPRECATION") private val vibrator = context.getSystemService(Vibrator::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()

    @Suppress("DEPRECATION") // AudioAttributes overload is needed on API 30–32.
    fun play(cue: Cue, sound: Boolean) {
        val pattern =
            when (cue) {
                Cue.WORK -> longArrayOf(0, 120, 80, 120, 80, 120)
                Cue.REST -> longArrayOf(0, 350, 150, 350)
                Cue.COMPLETE -> longArrayOf(0, 150, 100, 250, 100, 500)
                Cue.GENTLE -> longArrayOf(0, 100)
                Cue.NONE -> null
            }
        if (pattern != null && vibrator?.hasVibrator() == true) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build(),
                )
            } else {
                vibrator.vibrate(effect, attributes)
            }
        }
        if (sound) {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 65)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}
