package com.enclave.app.ui.lounge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.BackupManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.LetterDao
import com.enclave.app.data.local.LetterEntity
import com.enclave.app.network.BundleRepository
import com.enclave.app.network.LoungeSong
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.LenientJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class LoungePoint(val x: Float, val y: Float)

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
    val countdownLabel: String = ""
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

class LoungeViewModel(
    application: Application,
    private val signalingClient: SignalingClient,
    private val cryptoManager: CryptoManager,
    private val letterDao: LetterDao,
    private val database: EnclaveDatabase,
    private val partnerId: String,
    val myId: String,
    private val bundleRepository: BundleRepository? = null
) : AndroidViewModel(application) {

    private val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    private val prefs = application.getSharedPreferences("enclave_lounge_prefs_${myId}", Context.MODE_PRIVATE)

    // --- Profile & Mood States ---
    private val _myStatus = MutableStateFlow(ProfileStatus("❤️", "Online inside Enclave", 100, "Nothing playing", "12:00 PM"))
    val myStatus: StateFlow<ProfileStatus> = _myStatus.asStateFlow()

    private val _partnerStatus = MutableStateFlow(ProfileStatus("❤️", "Resting...", 100, "Offline", "12:00 PM"))
    val partnerStatus: StateFlow<ProfileStatus> = _partnerStatus.asStateFlow()

    // --- Dice Game States ---
    private val _isDiceRolling = MutableStateFlow(false)
    val isDiceRolling: StateFlow<Boolean> = _isDiceRolling.asStateFlow()

    private val _diceValue = MutableStateFlow(1)
    val diceValue: StateFlow<Int> = _diceValue.asStateFlow()

    private val _diceTickerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val diceTickerEvent: SharedFlow<Unit> = _diceTickerEvent.asSharedFlow()

    // --- Truth or Dare States ---
    private val _currentPrompt = MutableStateFlow("Tap 'Pick a Card' to start playing Truth or Dare...")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _isTruthSelected = MutableStateFlow(true)
    val isTruthSelected: StateFlow<Boolean> = _isTruthSelected.asStateFlow()

    // --- Live Drawing Canvas States ---
    val localStrokes = mutableStateListOf<LoungeStroke>()
    val partnerStrokes = mutableStateListOf<LoungeStroke>()

    private val _currentLocalStroke = MutableStateFlow<LoungeStroke?>(null)
    val currentLocalStroke: StateFlow<LoungeStroke?> = _currentLocalStroke.asStateFlow()

    private val _currentPartnerStroke = MutableStateFlow<LoungeStroke?>(null)
    val currentPartnerStroke: StateFlow<LoungeStroke?> = _currentPartnerStroke.asStateFlow()

    private val currentPointsBuffer = mutableListOf<LoungePoint>()
    private var batchJob: Job? = null


    // --- Daily Letter Capsule States ---
    val decryptedLettersFlow = letterDao.getAllLettersFlow()

    // Heartbeat Rate Limiter
    private var lastHeartbeatSentTime = 0L

    // --- Music Library States ---
    val loungeSongs = MutableStateFlow<List<LoungeSong>>(emptyList())
    val isUploading = MutableStateFlow(false)

    // --- Scratch-to-reveal Custom Photos ---
    private val _scratchImageBytes = MutableStateFlow<ByteArray?>(null)
    val scratchImageBytes: StateFlow<ByteArray?> = _scratchImageBytes.asStateFlow()

    init {
        // Load persistent countdown states
        val myCountdownLabel = prefs.getString("my_countdown_label", "") ?: ""
        val myCountdownTarget = prefs.getLong("my_countdown_target", 0L)
        _myStatus.value = _myStatus.value.copy(
            countdownLabel = myCountdownLabel,
            countdownTarget = myCountdownTarget
        )

        val partnerCountdownLabel = prefs.getString("partner_countdown_label", "") ?: ""
        val partnerCountdownTarget = prefs.getLong("partner_countdown_target", 0L)
        _partnerStatus.value = _partnerStatus.value.copy(
            countdownLabel = partnerCountdownLabel,
            countdownTarget = partnerCountdownTarget
        )

        observeSignaling()
        startStrokeBatchingScheduler()
        startProfileSyncTicker()
        refreshSongs()
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryStatus: Intent? = getApplication<Application>().registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
        } catch (e: Exception) {
            100
        }
    }

    private fun startProfileSyncTicker() {
        viewModelScope.launch {
            while (isActive) {
                syncMyStatus()
                delay(30_000L) // Synchronize every 30 seconds
            }
        }
    }

    fun updateMyStatus(emoji: String, statusText: String, nowListening: String) {
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val timeStr = formatter.format(java.util.Date())
        _myStatus.value = _myStatus.value.copy(
            moodEmoji = emoji,
            statusText = statusText,
            batteryPct = getBatteryPercentage(),
            nowListening = nowListening,
            localTimeStr = timeStr
        )
        syncMyStatus()
    }

    fun setCountdown(label: String, targetTimestamp: Long) {
        prefs.edit()
            .putString("my_countdown_label", label)
            .putLong("my_countdown_target", targetTimestamp)
            .apply()
        _myStatus.value = _myStatus.value.copy(
            countdownLabel = label,
            countdownTarget = targetTimestamp
        )
        syncMyStatus()
    }

    fun setScratchImage(bytes: ByteArray?) {
        _scratchImageBytes.value = bytes
        if (bytes != null) {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            sendLoungeMessage("LOUNGE_SCRATCH_UPLOAD", base64)
        } else {
            sendLoungeMessage("LOUNGE_SCRATCH_UPLOAD", "")
        }
    }

    private fun syncMyStatus() {
        viewModelScope.launch {
            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            val timeStr = formatter.format(java.util.Date())
            _myStatus.value = _myStatus.value.copy(
                batteryPct = getBatteryPercentage(),
                localTimeStr = timeStr
            )
            val json = Json.encodeToString(_myStatus.value)
            sendLoungeMessage("LOUNGE_PROFILE_UPDATE", json)
        }
    }

    // --- High-Performance Secure Backup Controllers ---
    fun exportSecureBackup(
        uri: android.net.Uri,
        passphrase: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val outputStream = resolver.openOutputStream(uri)
                    ?: throw Exception("Could not open output location")

                val result = BackupManager.exportBackup(getApplication(), outputStream, database, passphrase)
                
                viewModelScope.launch(Dispatchers.Main) {
                    if (result.isSuccess) onSuccess() else onFailure(result.exceptionOrNull() ?: Exception("Export failed"))
                }
            } catch (t: Throwable) {
                viewModelScope.launch(Dispatchers.Main) { onFailure(t) }
            }
        }
    }

    fun importSecureBackup(
        uri: android.net.Uri,
        passphrase: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val inputStream = resolver.openInputStream(uri)
                    ?: throw Exception("Could not open backup file")

                val result = BackupManager.importBackup(getApplication(), inputStream, database, passphrase)
                
                viewModelScope.launch(Dispatchers.Main) {
                    if (result.isSuccess) onSuccess() else onFailure(result.exceptionOrNull() ?: Exception("Import failed"))
                }
            } catch (t: Throwable) {
                viewModelScope.launch(Dispatchers.Main) { onFailure(t) }
            }
        }
    }

    // --- Music Library Actions ---
    fun refreshSongs() {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                loungeSongs.value = repo.fetchLoungeSongs()
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "refreshSongs failed", e)
            }
        }
    }

    fun uploadAndAddSong(title: String, bytes: ByteArray) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isUploading.value = true
            try {
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val url = repo.uploadMusicFile("${safeTitle}.mp3", bytes)
                repo.insertLoungeSong(title, url)
                refreshSongs()
                // Notify partner the playlist has been updated
                sendLoungeMessage("LOUNGE_PLAYLIST_UPDATE", "")
            } catch (t: Throwable) {
                android.util.Log.e("LoungeViewModel", "uploadAndAddSong failed", t)
                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Upload failed: ${t.localizedMessage ?: "Out of memory or network error"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } catch (ex: Exception) {
                        // Fail silently
                    }
                }
            } finally {
                isUploading.value = false
            }
        }
    }

    fun deleteSong(song: LoungeSong) {
        if (song.uploaded_by != myId) return  // Only uploader can delete
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteLoungeSong(song.id.orEmpty())
                refreshSongs()
                sendLoungeMessage("LOUNGE_PLAYLIST_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "deleteSong failed", e)
            }
        }
    }

    // --- Heartbeat Synchronization Actions ---
    fun sendHeartbeat() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatSentTime >= 800L) { // Explicit 800ms rate limiting
            lastHeartbeatSentTime = now
            sendLoungeMessage("LOUNGE_HEARTBEAT", "")
            triggerHeartbeatHaptic()
        }
    }

    fun triggerHeartbeatHaptic() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // High-fidelity tactile double heartbeat (beat-beat-pause pressure waveform)
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 80, 120),
                    intArrayOf(0, 180, 0, 255),
                    -1
                )
                vibratorManager.vibrate(CombinedVibration.createParallel(effect))
            } catch (_: Exception) {}
        }
    }

    // --- 3D Dice Tumbler Actions ---
    fun rollDice() {
        if (_isDiceRolling.value) return
        val finalRoll = (1..6).random()
        val event = SyncedDiceEvent(finalRoll, System.currentTimeMillis())
        sendLoungeMessage("LOUNGE_DICE_ROLL", Json.encodeToString(event))
        triggerLocalDiceAnimation(finalRoll)
    }

    private fun triggerLocalDiceAnimation(targetVal: Int) {
        viewModelScope.launch {
            _isDiceRolling.value = true
            val tickIntervals = listOf(50L, 50L, 50L, 80L, 80L, 120L, 180L, 250L, 350L)
            for (interval in tickIntervals) {
                _diceValue.value = (1..6).random()
                _diceTickerEvent.tryEmit(Unit)
                delay(interval)
            }
            _diceValue.value = targetVal
            _isDiceRolling.value = false
            _diceTickerEvent.tryEmit(Unit)
        }
    }

    // --- Truth or Dare Intimacy Deck ---
    private val truths = listOf(
        "What is your absolute favorite memory of us together?",
        "If you could whisper one secret into my ear right now, what would it be?",
        "What is a small habit of mine that secretly makes you blush?",
        "If we had only 24 hours left before a long separation, what would we do?",
        "What is the most vulnerable thing you've ever felt comfortable sharing with me?",
        "What was the exact moment you realized you had deep feelings for me?"
    )

    private val dares = listOf(
        "Send me a voice memo detailing your most vivid dream about me.",
        "Take a silly selfie right now and save it in our custom shared folder.",
        "Write me a custom daily love letter right now and capsule it in-memory.",
        "Gently blow a kiss to your screen and touch the gesture overlay canvas.",
        "Tell me a fantasy you have never spoken aloud until this exact moment.",
        "Whisper a naughty, cheeky voice memo and hide it inside the vaults."
    )

    fun pickTruthOrDare(isTruth: Boolean) {
        val promptsList = if (isTruth) truths else dares
        val prompt = promptsList.random()
        val index = promptsList.indexOf(prompt)
        val event = SyncedTruthOrDareEvent(index, isTruth, prompt)
        sendLoungeMessage("LOUNGE_TRUTH_OR_DARE", Json.encodeToString(event))
        _isTruthSelected.value = isTruth
        _currentPrompt.value = prompt
    }

    // --- Live Canvas Drawing Batched Synchronization ---
    fun startLocalStroke(x: Float, y: Float, colorHex: String, brushWidth: Float) {
        val startPoint = LoungePoint(x, y)
        _currentLocalStroke.value = LoungeStroke(listOf(startPoint), colorHex, brushWidth)
        synchronized(currentPointsBuffer) {
            currentPointsBuffer.clear()
            currentPointsBuffer.add(startPoint)
        }
        val event = LoungeDrawEvent("START", listOf(startPoint), colorHex, brushWidth)
        sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
    }

    fun addLocalStrokePoint(x: Float, y: Float) {
        val nextPoint = LoungePoint(x, y)
        val active = _currentLocalStroke.value
        if (active != null) {
            _currentLocalStroke.value = active.copy(points = active.points + nextPoint)
        }
        synchronized(currentPointsBuffer) {
            currentPointsBuffer.add(nextPoint)
        }
    }

    fun finalizeLocalStroke() {
        val active = _currentLocalStroke.value
        if (active != null) {
            localStrokes.add(active)
            _currentLocalStroke.value = null
        }
        val remaining = synchronized(currentPointsBuffer) {
            val list = currentPointsBuffer.toList()
            currentPointsBuffer.clear()
            list
        }
        val event = LoungeDrawEvent("END", remaining, active?.colorHex ?: "#E598A7", active?.brushWidth ?: 8f)
        sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
    }

    fun clearCanvas() {
        localStrokes.clear()
        partnerStrokes.clear()
        _currentLocalStroke.value = null
        _currentPartnerStroke.value = null
        sendLoungeMessage("LOUNGE_CANVAS_CLEAR", "")
    }

    private fun startStrokeBatchingScheduler() {
        batchJob = viewModelScope.launch {
            while (isActive) {
                delay(50L) // 50ms batching for smooth and efficient remote sync
                flushStrokeBatch()
            }
        }
    }

    private fun flushStrokeBatch() {
        val pointsToFlush = synchronized(currentPointsBuffer) {
            if (currentPointsBuffer.size > 1) {
                val list = currentPointsBuffer.toList()
                val lastPoint = currentPointsBuffer.last()
                currentPointsBuffer.clear()
                currentPointsBuffer.add(lastPoint)
                list
            } else null
        }

        if (pointsToFlush != null) {
            val active = _currentLocalStroke.value
            val event = LoungeDrawEvent(
                action = "MOVE",
                points = pointsToFlush,
                colorHex = active?.colorHex ?: "#E598A7",
                brushWidth = active?.brushWidth ?: 8f
            )
            viewModelScope.launch {
                sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
            }
        }
    }


    // --- Encrypted Daily Love Letters Capsuling ---
    fun sendLetter(content: String) {
        viewModelScope.launch {
            val payload = SyncedLetterPayload(myId, content)
            sendLoungeMessage("LOUNGE_LETTER_SEND", Json.encodeToString(payload))

            val encryptedB64 = cryptoManager.encryptLocal(content.toByteArray(Charsets.UTF_8))
            val letterEntity = LetterEntity(
                id = UUID.randomUUID().toString(),
                senderId = myId,
                ciphertext = encryptedB64,
                createdAt = System.currentTimeMillis(),
                isRead = true
            )
            letterDao.insertLetter(letterEntity)
        }
    }

    fun decryptLetter(ciphertext: String): String {
        return try {
            val bytes = cryptoManager.decryptLocal(ciphertext)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Decryption Error"
        }
    }

    fun markLetterAsRead(id: String) {
        viewModelScope.launch {
            letterDao.markAsRead(id)
        }
    }

    // --- WebSocket Signaling Handlers ---
    private fun sendLoungeMessage(type: String, payload: String) {
        viewModelScope.launch {
            val wrapper = SignalMessageWrapper(
                type = type,
                senderId = myId,
                targetId = partnerId,
                payload = payload
            )
            signalingClient.sendRawMessage(Json.encodeToString(wrapper))
        }
    }

    private fun observeSignaling() {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { rawText ->
                try {
                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(rawText)
                    if (msg.senderId != partnerId) return@collect

                    when (msg.type) {
                        "LOUNGE_HEARTBEAT" -> {
                            triggerHeartbeatHaptic()
                        }
                        "LOUNGE_PROFILE_UPDATE" -> {
                            msg.payload?.let {
                                val status = LenientJson.decodeFromString<ProfileStatus>(it)
                                _partnerStatus.value = status
                                prefs.edit()
                                    .putString("partner_countdown_label", status.countdownLabel)
                                    .putLong("partner_countdown_target", status.countdownTarget)
                                    .apply()
                            }
                        }
                        "LOUNGE_SCRATCH_UPLOAD" -> {
                            msg.payload?.let { base64 ->
                                if (base64.isNotEmpty()) {
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                                    _scratchImageBytes.value = bytes
                                } else {
                                    _scratchImageBytes.value = null
                                }
                            }
                        }
                        "LOUNGE_DICE_ROLL" -> {
                            msg.payload?.let {
                                val event = LenientJson.decodeFromString<SyncedDiceEvent>(it)
                                triggerLocalDiceAnimation(event.rolledValue)
                            }
                        }
                        "LOUNGE_TRUTH_OR_DARE" -> {
                            msg.payload?.let {
                                val event = LenientJson.decodeFromString<SyncedTruthOrDareEvent>(it)
                                _isTruthSelected.value = event.isTruth
                                _currentPrompt.value = event.prompt
                            }
                        }
                        "LOUNGE_CANVAS_EVENT" -> {
                            msg.payload?.let {
                                val event = LenientJson.decodeFromString<LoungeDrawEvent>(it)
                                when (event.action) {
                                    "START" -> {
                                        _currentPartnerStroke.value = LoungeStroke(event.points, event.colorHex, event.brushWidth)
                                    }
                                    "MOVE" -> {
                                        val active = _currentPartnerStroke.value
                                        if (active != null) {
                                            _currentPartnerStroke.value = active.copy(points = active.points + event.points)
                                        } else {
                                            _currentPartnerStroke.value = LoungeStroke(event.points, event.colorHex, event.brushWidth)
                                        }
                                    }
                                    "END" -> {
                                        val active = _currentPartnerStroke.value
                                        val finalStroke = if (active != null) {
                                            active.copy(points = active.points + event.points)
                                        } else {
                                            LoungeStroke(event.points, event.colorHex, event.brushWidth)
                                        }
                                        partnerStrokes.add(finalStroke)
                                        _currentPartnerStroke.value = null
                                    }
                                }
                            }
                        }
                        "LOUNGE_CANVAS_STROKE_BATCH" -> {
                            msg.payload?.let {
                                val stroke = LenientJson.decodeFromString<LoungeStroke>(it)
                                partnerStrokes.add(stroke)
                            }
                        }
                        "LOUNGE_CANVAS_STROKE" -> {
                            msg.payload?.let {
                                val stroke = LenientJson.decodeFromString<LoungeStroke>(it)
                                partnerStrokes.add(stroke)
                            }
                        }
                        "LOUNGE_CANVAS_CLEAR" -> {
                            localStrokes.clear()
                            partnerStrokes.clear()
                            _currentLocalStroke.value = null
                            _currentPartnerStroke.value = null
                        }
                        "LOUNGE_LETTER_SEND" -> {
                            msg.payload?.let {
                                val payload = LenientJson.decodeFromString<SyncedLetterPayload>(it)
                                val encryptedB64 = cryptoManager.encryptLocal(payload.plainContent.toByteArray(Charsets.UTF_8))
                                val letterEntity = LetterEntity(
                                    id = UUID.randomUUID().toString(),
                                    senderId = payload.senderId,
                                    ciphertext = encryptedB64,
                                    createdAt = System.currentTimeMillis(),
                                    isRead = false
                                )
                                letterDao.insertLetter(letterEntity)
                            }
                        }
                        "LOUNGE_PLAYLIST_UPDATE" -> {
                            // Partner added or removed a song — refresh our local list
                            refreshSongs()
                        }
                    }
                } catch (e: Exception) {
                    // Fail silently on non-lounge wrapper parse
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        batchJob?.cancel()
    }
}
