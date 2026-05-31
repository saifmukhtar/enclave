package dev.saifmukhtar.enclave.ui.lounge

import dev.saifmukhtar.enclave.webrtc.SignalingClient
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.LenientJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

sealed class LoungeIncomingEvent {
    object Heartbeat : LoungeIncomingEvent()
    data class ProfileUpdate(val status: ProfileStatus) : LoungeIncomingEvent()
    data class ScratchUpload(val bytes: ByteArray?) : LoungeIncomingEvent()
    object ScratchSeen : LoungeIncomingEvent()
    object ScratchDestroyed : LoungeIncomingEvent()
    data class DiceRoll(val event: SyncedDiceEvent) : LoungeIncomingEvent()
    data class TruthOrDare(val event: SyncedTruthOrDareEvent) : LoungeIncomingEvent()
    data class CanvasEvent(val event: LoungeDrawEvent) : LoungeIncomingEvent()
    object CanvasClear : LoungeIncomingEvent()
    data class NoteSync(val payload: SyncedNotePayload) : LoungeIncomingEvent()
    data class NoteDelete(val id: String) : LoungeIncomingEvent()
    data class LetterSend(val payload: SyncedLetterPayload) : LoungeIncomingEvent()
    data class CanvasStrokeBatch(val stroke: LoungeStroke) : LoungeIncomingEvent()
    data class CanvasStroke(val stroke: LoungeStroke) : LoungeIncomingEvent()
    object PlaylistUpdate : LoungeIncomingEvent()
    object DrawingsUpdate : LoungeIncomingEvent()
    object ScrapbookUpdate : LoungeIncomingEvent()
    object QueueUpdate : LoungeIncomingEvent()
    data class QuizCompleted(val dominantLabel: String) : LoungeIncomingEvent()
}

// SyncedNotePayload needs to be moved here to be visible, or we access it from LoungeViewModel.
// Let's define it in LoungeModels later, but for now we put it here.
@kotlinx.serialization.Serializable
data class SyncedNotePayload(
    val id: String,
    val titlePayloadBase64: String,
    val contentPayloadBase64: String,
    val authorId: String
)

class LoungeSyncUseCase(
    private val signalingClient: SignalingClient,
    private val myId: String,
    private val partnerId: String
) {
    fun observeEvents(): Flow<LoungeIncomingEvent> {
        return signalingClient.incomingRawMessages.mapNotNull { rawText ->
            try {
                val msg = LenientJson.decodeFromString<SignalMessageWrapper>(rawText)
                if (msg.senderId != partnerId) return@mapNotNull null

                when (msg.type) {
                    "LOUNGE_HEARTBEAT" -> LoungeIncomingEvent.Heartbeat
                    "LOUNGE_PROFILE_UPDATE" -> {
                        msg.payload?.let {
                            val status = LenientJson.decodeFromString<ProfileStatus>(it)
                            LoungeIncomingEvent.ProfileUpdate(status)
                        }
                    }
                    "LOUNGE_SCRATCH_UPLOAD" -> {
                        msg.payload?.let { base64 ->
                            if (base64.isNotEmpty()) {
                                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                                LoungeIncomingEvent.ScratchUpload(bytes)
                            } else {
                                LoungeIncomingEvent.ScratchUpload(null)
                            }
                        }
                    }
                    "LOUNGE_SCRATCH_SEEN" -> LoungeIncomingEvent.ScratchSeen
                    "LOUNGE_SCRATCH_DESTROYED" -> LoungeIncomingEvent.ScratchDestroyed
                    "LOUNGE_DICE_ROLL" -> {
                        msg.payload?.let {
                            val event = LenientJson.decodeFromString<SyncedDiceEvent>(it)
                            LoungeIncomingEvent.DiceRoll(event)
                        }
                    }
                    "LOUNGE_TRUTH_OR_DARE" -> {
                        msg.payload?.let {
                            val event = LenientJson.decodeFromString<SyncedTruthOrDareEvent>(it)
                            LoungeIncomingEvent.TruthOrDare(event)
                        }
                    }
                    "LOUNGE_CANVAS_EVENT" -> {
                        msg.payload?.let {
                            val event = LenientJson.decodeFromString<LoungeDrawEvent>(it)
                            LoungeIncomingEvent.CanvasEvent(event)
                        }
                    }
                    "LOUNGE_CANVAS_STROKE_BATCH" -> {
                        msg.payload?.let {
                            val stroke = LenientJson.decodeFromString<LoungeStroke>(it)
                            LoungeIncomingEvent.CanvasStrokeBatch(stroke)
                        }
                    }
                    "LOUNGE_CANVAS_STROKE" -> {
                        msg.payload?.let {
                            val stroke = LenientJson.decodeFromString<LoungeStroke>(it)
                            LoungeIncomingEvent.CanvasStroke(stroke)
                        }
                    }
                    "LOUNGE_CANVAS_CLEAR" -> LoungeIncomingEvent.CanvasClear
                    "LOUNGE_NOTE_SYNC" -> {
                        msg.payload?.let {
                            val payload = LenientJson.decodeFromString<SyncedNotePayload>(it)
                            LoungeIncomingEvent.NoteSync(payload)
                        }
                    }
                    "LOUNGE_NOTE_DELETE" -> {
                        msg.payload?.let { LoungeIncomingEvent.NoteDelete(it) }
                    }
                    "LOUNGE_LETTER_SEND" -> {
                        msg.payload?.let {
                            val payload = LenientJson.decodeFromString<SyncedLetterPayload>(it)
                            LoungeIncomingEvent.LetterSend(payload)
                        }
                    }
                    "LOUNGE_PLAYLIST_UPDATE" -> LoungeIncomingEvent.PlaylistUpdate
                    "LOUNGE_DRAWINGS_UPDATE" -> LoungeIncomingEvent.DrawingsUpdate
                    "LOUNGE_SCRAPBOOK_UPDATE" -> LoungeIncomingEvent.ScrapbookUpdate
                    "LOUNGE_QUEUE_UPDATE" -> LoungeIncomingEvent.QueueUpdate
                    "LOUNGE_QUIZ_COMPLETED" -> {
                        msg.payload?.let { LoungeIncomingEvent.QuizCompleted(it) }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun sendMessage(type: String, payload: String) {
        val wrapper = SignalMessageWrapper(
            type = type,
            senderId = myId,
            targetId = partnerId,
            payload = payload
        )
        signalingClient.sendRawMessage(Json.encodeToString(wrapper))
    }
}
