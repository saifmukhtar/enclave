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
import com.enclave.app.data.local.UserProfileEntity
import com.enclave.app.network.BundleRepository
import com.enclave.app.network.LoungeSong
import com.enclave.app.network.LoungeDrawing
import com.enclave.app.network.ScrapbookEntry
import com.enclave.app.network.LoungeQueueItem
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
import kotlinx.coroutines.withContext
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

    // --- Drawings board gallery ---
    val loungeDrawings = MutableStateFlow<List<LoungeDrawing>>(emptyList())
    val isDrawingUploading = MutableStateFlow(false)

    // --- Weather Sync Cache ---
    private var cachedWeather: Pair<Double, String>? = null
    private var lastWeatherFetchTime = 0L

    // --- Quiz Question List ---
    val quizQuestions = listOf(
        QuizQuestion(1, "I like to receive notes of appreciation.", "A", "I like to be hugged.", "E"),
        QuizQuestion(2, "I like to spend one-on-one time with you.", "B", "I feel loved when you help me with a chore.", "D"),
        QuizQuestion(3, "I love receiving small gifts from you.", "C", "I love going on walks or trips together.", "B"),
        QuizQuestion(4, "I feel valued when you praise my achievements.", "A", "I feel loved when you do the dishes or clean up.", "D"),
        QuizQuestion(5, "I love it when you hold my hand in public.", "E", "I love when you surprise me with a small present.", "C"),
        QuizQuestion(6, "I like to hear you say 'I love you'.", "A", "I like when we sit close and talk for hours.", "B"),
        QuizQuestion(7, "I value when you help me when I'm tired.", "D", "I value receiving a thoughtful gift.", "C"),
        QuizQuestion(8, "I feel secure when you touch my arm or shoulder.", "E", "I feel happy when we do something creative together.", "B"),
        QuizQuestion(9, "I appreciate when you write a sweet text message.", "A", "I appreciate when you make me dinner.", "D"),
        QuizQuestion(10, "I love receiving holiday or birthday gifts from you.", "C", "I love when you kiss me hello and goodbye.", "E"),
        QuizQuestion(11, "I love having your undivided attention.", "B", "I love when you help me fix something.", "D"),
        QuizQuestion(12, "I feel loved when you buy me something I wanted.", "C", "I feel loved when you encourage me.", "A"),
        QuizQuestion(13, "I love when we cuddle on the couch.", "E", "I love when we make breakfast together.", "B"),
        QuizQuestion(14, "I appreciate when you take care of tasks for me.", "D", "I appreciate when you give me verbal compliments.", "A"),
        QuizQuestion(15, "I like when we hug tightly.", "E", "I like when you bring me coffee or a treat.", "C")
    )

    // --- Scrapbook States ---
    val scrapbookEntries = MutableStateFlow<List<ScrapbookEntry>>(emptyList())
    val isScrapbookUploading = MutableStateFlow(false)

    // --- Playlist Queue States ---
    val playlistQueue = MutableStateFlow<List<LoungeQueueItem>>(emptyList())

    // --- User profile states for Love Language quiz side-by-side display ---
    val myProfile = MutableStateFlow<UserProfileEntity?>(null)
    val partnerProfile = MutableStateFlow<UserProfileEntity?>(null)

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
        refreshDrawings()
        refreshScrapbook()
        refreshQueue()
        refreshProfiles()
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

    private suspend fun fetchWeatherForCity(city: String): Pair<Double, String>? {
        if (city.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                // 1. Geocode city to lat/lon
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(city, "UTF-8")}&count=1&language=en&format=json"
                val geoRequest = okhttp3.Request.Builder().url(geoUrl).build()
                val geoResponse = client.newCall(geoRequest).execute()
                if (!geoResponse.isSuccessful) return@withContext null
                val geoBody = geoResponse.body?.string() ?: return@withContext null
                val geoJson = org.json.JSONObject(geoBody)
                val results = geoJson.optJSONArray("results")
                if (results == null || results.length() == 0) return@withContext null
                val firstResult = results.getJSONObject(0)
                val lat = firstResult.getDouble("latitude")
                val lon = firstResult.getDouble("longitude")

                // 2. Fetch current temperature & weather code
                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                val weatherRequest = okhttp3.Request.Builder().url(weatherUrl).build()
                val weatherResponse = client.newCall(weatherRequest).execute()
                if (!weatherResponse.isSuccessful) return@withContext null
                val weatherBody = weatherResponse.body?.string() ?: return@withContext null
                val weatherJson = org.json.JSONObject(weatherBody)
                val current = weatherJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")

                // Map WMO code to condition emoji
                val conditionEmoji = when (code) {
                    0 -> "☀️" // Clear sky
                    1, 2, 3 -> "🌤️" // Mainly clear, partly cloudy, and overcast
                    45, 48 -> "🌫️" // Fog and depositing rime fog
                    51, 53, 55 -> "🌧️" // Drizzle
                    61, 63, 65 -> "🌧️" // Rain
                    71, 73, 75 -> "❄️" // Snow fall
                    77 -> "❄️" // Snow grains
                    80, 81, 82 -> "🌧️" // Rain showers
                    85, 86 -> "❄️" // Snow showers
                    95 -> "⛈️" // Thunderstorm
                    96, 99 -> "⛈️" // Thunderstorm with hail
                    else -> "🌤️"
                }
                Pair(temp, conditionEmoji)
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "Failed to fetch weather for $city", e)
                null
            }
        }
    }

    private fun syncMyStatus() {
        viewModelScope.launch {
            val myProfileVal = bundleRepository?.fetchMyProfile()
            val city = myProfileVal?.locationCity.orEmpty()
            
            // Refresh weather every 15 minutes if city is set
            val now = System.currentTimeMillis()
            if (city.isNotEmpty() && (cachedWeather == null || now - lastWeatherFetchTime > 15 * 60 * 1000L)) {
                val weather = fetchWeatherForCity(city)
                if (weather != null) {
                    cachedWeather = weather
                    lastWeatherFetchTime = now
                }
            }

            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            val timeStr = formatter.format(java.util.Date())
            
            _myStatus.value = _myStatus.value.copy(
                batteryPct = getBatteryPercentage(),
                localTimeStr = timeStr,
                weatherTemp = cachedWeather?.first ?: -999.0,
                weatherCondition = cachedWeather?.second ?: ""
            )
            val json = Json.encodeToString(_myStatus.value)
            sendLoungeMessage("LOUNGE_PROFILE_UPDATE", json)
        }
    }

    // --- Profile Management ---
    fun refreshProfiles() {
        viewModelScope.launch {
            val repo = bundleRepository ?: return@launch
            myProfile.value = repo.fetchMyProfile()
            partnerProfile.value = repo.fetchPartnerProfile(partnerId)
        }
    }

    fun updateProfileLocation(city: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            repo.updateLocationCity(city)
            refreshProfiles()
            cachedWeather = null // Reset cache to force immediate reload
            syncMyStatus()
        }
    }

    // --- Love Language Quiz ---
    fun submitQuizResults(answers: List<String>) {
        val counts = answers.groupingBy { it }.eachCount()
        val dominant = counts.maxByOrNull { it.value }?.key ?: "A"
        val dominantLabel = when (dominant) {
            "A" -> "Words of Affirmation"
            "B" -> "Quality Time"
            "C" -> "Receiving Gifts"
            "D" -> "Acts of Service"
            "E" -> "Physical Touch"
            else -> "Words of Affirmation"
        }
        viewModelScope.launch {
            bundleRepository?.updateLoveLanguage(dominantLabel)
            sendLoungeMessage("LOUNGE_QUIZ_COMPLETED", dominantLabel)
            refreshProfiles()
        }
    }

    // --- Shared Drawings board gallery ---
    fun refreshDrawings() {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                loungeDrawings.value = repo.fetchLoungeDrawings()
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "refreshDrawings failed", e)
            }
        }
    }

    fun uploadAndAddDrawing(title: String, bytes: ByteArray) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isDrawingUploading.value = true
            try {
                val fileName = "drawing_${System.currentTimeMillis()}.png"
                val url = repo.uploadDrawingFile(fileName, bytes)
                repo.insertLoungeDrawing(title, url)
                refreshDrawings()
                sendLoungeMessage("LOUNGE_DRAWINGS_UPDATE", "")
            } catch (t: Throwable) {
                android.util.Log.e("LoungeViewModel", "uploadAndAddDrawing failed", t)
            } finally {
                isDrawingUploading.value = false
            }
        }
    }

    fun deleteDrawing(drawing: LoungeDrawing) {
        if (drawing.uploaded_by != myId) return
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteLoungeDrawing(drawing.id.orEmpty())
                refreshDrawings()
                sendLoungeMessage("LOUNGE_DRAWINGS_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "deleteDrawing failed", e)
            }
        }
    }

    fun saveCanvasToGallery(title: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isDrawingUploading.value = true
            try {
                val size = 800
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }

                fun drawStrokeToBitmap(stroke: LoungeStroke) {
                    if (stroke.points.isEmpty()) return
                    try {
                        paint.color = android.graphics.Color.parseColor(stroke.colorHex)
                    } catch (e: Exception) {
                        paint.color = android.graphics.Color.BLACK
                    }
                    paint.strokeWidth = stroke.brushWidth

                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * size, stroke.points[0].y * size)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x * size, stroke.points[i].y * size)
                    }
                    canvas.drawPath(path, paint)
                }

                val allStrokes = localStrokes.toList() + partnerStrokes.toList()
                allStrokes.forEach { drawStrokeToBitmap(it) }

                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val bytes = outputStream.toByteArray()

                val fileName = "drawing_${System.currentTimeMillis()}.png"
                val url = repo.uploadDrawingFile(fileName, bytes)
                repo.insertLoungeDrawing(title, url)

                refreshDrawings()
                sendLoungeMessage("LOUNGE_DRAWINGS_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "saveCanvasToGallery failed", e)
            } finally {
                isDrawingUploading.value = false
            }
        }
    }


    // --- Scrapbook ---
    fun refreshScrapbook() {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                scrapbookEntries.value = repo.fetchScrapbookEntries()
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "refreshScrapbook failed", e)
            }
        }
    }

    fun uploadAndAddScrapbook(caption: String, eventDate: String, bytes: ByteArray) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isScrapbookUploading.value = true
            try {
                val fileName = "scrapbook_${System.currentTimeMillis()}.jpg"
                val url = repo.uploadScrapbookPhoto(fileName, bytes)
                repo.insertScrapbookEntry(caption, url, eventDate)
                refreshScrapbook()
                sendLoungeMessage("LOUNGE_SCRAPBOOK_UPDATE", "")
            } catch (t: Throwable) {
                android.util.Log.e("LoungeViewModel", "uploadAndAddScrapbook failed", t)
            } finally {
                isScrapbookUploading.value = false
            }
        }
    }

    fun deleteScrapbookEntry(entry: ScrapbookEntry) {
        if (entry.uploaded_by != myId) return
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteScrapbookEntry(entry.id.orEmpty())
                refreshScrapbook()
                sendLoungeMessage("LOUNGE_SCRAPBOOK_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "deleteScrapbookEntry failed", e)
            }
        }
    }

    // --- Playlist Queue ---
    fun refreshQueue() {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                playlistQueue.value = repo.fetchLoungeQueue()
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "refreshQueue failed", e)
            }
        }
    }

    fun addToQueue(songId: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                repo.insertQueueItem(songId)
                refreshQueue()
                sendLoungeMessage("LOUNGE_QUEUE_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "addToQueue failed", e)
            }
        }
    }

    fun removeFromQueue(id: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteQueueItem(id)
                refreshQueue()
                sendLoungeMessage("LOUNGE_QUEUE_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeViewModel", "removeFromQueue failed", e)
            }
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
                        "LOUNGE_DRAWINGS_UPDATE" -> {
                            refreshDrawings()
                        }
                        "LOUNGE_SCRAPBOOK_UPDATE" -> {
                            refreshScrapbook()
                        }
                        "LOUNGE_QUEUE_UPDATE" -> {
                            refreshQueue()
                        }
                        "LOUNGE_QUIZ_COMPLETED" -> {
                            refreshProfiles()
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
