package com.walktalk.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sin

class AudioFeedback(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Play a short rising chime (Chirp) when PTT button is pressed
     */
    fun playPttStartChirp() {
        vibrate(30)
        playTone(frequencyStart = 880f, frequencyEnd = 1320f, durationMs = 60)
    }

    /**
     * Play a short falling tone (Click) when PTT button is released
     */
    fun playPttReleaseClick() {
        vibrate(20)
        playTone(frequencyStart = 1100f, frequencyEnd = 660f, durationMs = 40)
    }

    /**
     * Play a low buzz on error / network unavailable
     */
    fun playErrorBuzz() {
        vibrate(150)
        playTone(frequencyStart = 220f, frequencyEnd = 220f, durationMs = 150)
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun playTone(frequencyStart: Float, frequencyEnd: Float, durationMs: Int) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val currentFreq = frequencyStart + (frequencyEnd - frequencyStart) * progress
                    val angle = 2.0 * Math.PI * i / (sampleRate / currentFreq)
                    // Apply envelope to prevent audio pop/click
                    val envelope = sin(Math.PI * progress).toFloat()
                    buffer[i] = (sin(angle) * 32767 * envelope * 0.4f).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 20)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.start()
    }
}
