package com.enclave.app.ui.kiss.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.sin

class KissAudioSynthesizer {
    private val sampleRate = 22050
    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var phase = 0.0
    private var currentFrequency = 60.0
    private var currentVolume = 0.0f
    private var synthThread: Thread? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        
        try {
            @Suppress("DEPRECATION")
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(4096),
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        synthThread = Thread {
            val buffer = ShortArray(1024)
            while (isPlaying) {
                val track = audioTrack ?: break
                val vol = currentVolume
                val freq = currentFrequency
                
                for (i in buffer.indices) {
                    if (vol < 0.005f) {
                        buffer[i] = 0
                    } else {
                        // Pure, warm analog-like sub-bass sine wave
                        val sample = sin(phase) * 32767.0 * vol * 0.4
                        buffer[i] = sample.toInt().toShort()
                        
                        val phaseIncrement = (2.0 * Math.PI * freq) / sampleRate
                        phase += phaseIncrement
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }
                    }
                }
                try {
                    track.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun updateTelemetry(kineticEnergy: Float, meshStress: Float) {
        // Map kinetic energy directly to volume
        currentVolume = (kineticEnergy * 8.0f).coerceIn(0f, 1f)
        // Modulate sub-bass pitch based on mesh spring stress constraint
        currentFrequency = 50.0 + (meshStress * 180.0).coerceIn(0.0, 45.0)
    }

    fun stop() {
        isPlaying = false
        synthThread?.interrupt()
        synthThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
