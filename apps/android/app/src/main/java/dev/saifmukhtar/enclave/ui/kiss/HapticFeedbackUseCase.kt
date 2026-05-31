package dev.saifmukhtar.enclave.ui.kiss

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class HapticFeedbackUseCase(context: Context) {

    private val vibrator by lazy {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    suspend fun startMutualHapticLoop(
        remoteIntensityFlow: StateFlow<Float>,
        localIntensityFlow: StateFlow<Float>,
        partnerSignatureSeed: Int,
        partnerStyleIntensityFlow: () -> Float,
        partnerStyleVelocityFlow: () -> Float,
        isMutualPressActive: () -> Boolean
    ) {
        val vibe = vibrator
        while (coroutineContext.isActive && isMutualPressActive()) {
            try {
                // Blend both partners' active touch intensities for mutual sensation.
                val blendedIntensity = ((remoteIntensityFlow.value * 0.62f) + (localIntensityFlow.value * 0.38f))
                    .coerceIn(0.08f, 1.0f)
                val signature = buildSignaturePattern(
                    partnerSignatureSeed, 
                    blendedIntensity, 
                    partnerStyleIntensityFlow(), 
                    partnerStyleVelocityFlow()
                )

                vibe.vibrate(VibrationEffect.createWaveform(signature.timings, signature.amplitudes, -1))
                delay(signature.totalDurationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                try { vibe.vibrate(VibrationEffect.createOneShot(100, 150)) } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    fun stopHaptics() {
        try {
            vibrator.cancel()
        } catch (_: Exception) {}
    }

    private data class SignaturePattern(
        val timings: LongArray,
        val amplitudes: IntArray,
        val totalDurationMs: Long
    )

    private fun buildSignaturePattern(
        seed: Int, 
        intensity: Float,
        partnerStyleIntensity: Float,
        partnerStyleVelocity: Float
    ): SignaturePattern {
        val personalizedIntensity = (intensity * 0.7f + partnerStyleIntensity * 0.3f).coerceIn(0f, 1f)
        val personalizedVelocity = partnerStyleVelocity.coerceIn(0f, 1f)
        val profile = when {
            personalizedIntensity < 0.34f -> "soft"
            personalizedIntensity < 0.72f -> "steady"
            else -> "intense"
        }
        val baseAmp = (70 + personalizedIntensity * 185f).toInt().coerceIn(50, 255)
        val paceShift = (personalizedVelocity * 18f).toLong()
        val timings: LongArray
        val amplitudes: IntArray
        when (profile) {
            "soft" -> {
                val p1 = 52L + (seed % 19)
                val p2 = 66L + (seed % 23)
                val gap = (70L + (seed % 21) - paceShift).coerceAtLeast(30L)
                timings = longArrayOf(0L, p1, gap, p2, gap + 20L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.55f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.72f).toInt().coerceIn(1, 255), 0
                )
            }
            "steady" -> {
                val p1 = 40L + (seed % 17)
                val p2 = 48L + (seed % 15)
                val p3 = 56L + (seed % 19)
                val gap = (46L + (seed % 18) - paceShift).coerceAtLeast(20L)
                timings = longArrayOf(0L, p1, gap, p2, gap, p3, gap + 8L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.66f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.84f).toInt().coerceIn(1, 255), 0,
                    baseAmp, 0
                )
            }
            else -> {
                val p1 = 30L + (seed % 13)
                val p2 = 34L + (seed % 11)
                val p3 = 40L + (seed % 15)
                val p4 = 48L + (seed % 17)
                val gap = (24L + (seed % 12) - paceShift).coerceAtLeast(12L)
                timings = longArrayOf(0L, p1, gap, p2, gap, p3, gap, p4, gap + 6L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.70f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.85f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.94f).toInt().coerceIn(1, 255), 0,
                    baseAmp, 0
                )
            }
        }
        return SignaturePattern(
            timings = timings,
            amplitudes = amplitudes,
            totalDurationMs = timings.sum().coerceAtLeast(120L)
        )
    }
}
