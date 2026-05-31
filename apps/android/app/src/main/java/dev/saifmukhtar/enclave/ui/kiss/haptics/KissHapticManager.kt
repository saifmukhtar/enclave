package dev.saifmukhtar.enclave.ui.kiss.haptics

import android.content.Context
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager

class KissHapticManager(context: Context) {
    private val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    fun processEngineState(kineticEnergy: Float, meshStress: Float) {
        // Only trigger haptics if the mesh is undergoing meaningful activity
        if (kineticEnergy > 0.008f) {
            // Map kinetic energy to an amplitude constraint (1 to 255)
            val amplitude = (kineticEnergy * 2500f).coerceIn(1f, 255f).toInt()

            // Map structural mesh stress to duration frequency pulses (8ms to 25ms)
            val pulseDuration = (meshStress * 300f).coerceIn(8f, 25f).toLong()

            try {
                val effect = VibrationEffect.createOneShot(pulseDuration, amplitude)
                vibratorManager.vibrate(CombinedVibration.createParallel(effect))
            } catch (_: Exception) {
                // Fail silently to prevent background thread crashes
            }
        }
    }
}
