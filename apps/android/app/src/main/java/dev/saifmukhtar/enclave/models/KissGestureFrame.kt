package dev.saifmukhtar.enclave.models

import kotlinx.serialization.Serializable

/**
 * A single kiss touch frame with position, pressure (normalised 0–1), and touch radius.
 */
@Serializable
data class KissGestureFrame(
    val xPct: Float,
    val yPct: Float,
    val action: String,                   // "KISS_TOUCH_DOWN" | "KISS_TOUCH_MOVE" | "KISS_TOUCH_UP"
    val pressure: Float = 0.5f,           // Finger press pressure 0–1
    val touchRadius: Float = 30f,         // Pointer tool major axis (approximate lip blob size)
    val fingerIndex: Int = 0,             // Multi-touch finger index for lip impression shape
    val touchMajor: Float = 0f,
    val touchMinor: Float = 0f,
    val orientation: Float = 0f
)

/**
 * A complete lip impression snapshot — all active touch points at one moment.
 */
@Serializable
data class LipImpressionFrame(
    val points: List<KissGestureFrame>,
    val isMutualPress: Boolean = false,   // true when both sides are pressing simultaneously
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0L,
    val sourceSessionId: String = ""
)

/**
 * A single recorded kiss coordinate, pressure, and timing offset.
 */
@Serializable
data class RecordedKissPoint(
    val xPct: Float,
    val yPct: Float,
    val pressure: Float,
    val touchRadius: Float,
    val timeOffsetMs: Long
)

/**
 * A sequence of touch frames forming a recorded kiss.
 */
@Serializable
data class RecordedKissPayload(
    val points: List<RecordedKissPoint>
)
