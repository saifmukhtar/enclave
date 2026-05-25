package com.enclave.app.ui.lounge

import kotlinx.serialization.Serializable

@Serializable
data class LoungePoint(val x: Float, val y: Float)

data class ScratchState(
    val bytes: ByteArray,
    val isSender: Boolean,
    val isSeen: Boolean = false,
    val isDestroyed: Boolean = false
)

data class DrawingStroke(
    val points: List<LoungePoint>, val colorHex: String, val brushWidth: Float)

@Serializable
data class LoungeStroke(val points: List<LoungePoint>, val colorHex: String, val brushWidth: Float)

@Serializable
data class LoungeDrawEvent(
    val action: String, // "START", "MOVE", "END"
    val points: List<LoungePoint>,
    val colorHex: String,
    val brushWidth: Float
)


@Serializable
data class ProfileStatus(
    val moodEmoji: String,
    val statusText: String,
    val batteryPct: Int,
    val nowListening: String,
    val localTimeStr: String,
    val countdownTarget: Long = 0L,
    val countdownLabel: String = "",
    val weatherTemp: Double = -999.0,
    val weatherCondition: String = ""
)

@Serializable
data class QuizQuestion(
    val id: Int,
    val optionA: String,
    val optionACategory: String,
    val optionB: String,
    val optionBCategory: String
)


@Serializable
data class SyncedDiceEvent(
    val rolledValue: Int,
    val seed: Long
)

@Serializable
data class SyncedTruthOrDareEvent(
    val cardIndex: Int,
    val isTruth: Boolean,
    val prompt: String
)

@Serializable
data class SyncedLetterPayload(
    val senderId: String,
    val plainContent: String
)
