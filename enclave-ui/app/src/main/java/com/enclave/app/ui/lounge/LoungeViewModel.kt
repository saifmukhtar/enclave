package com.enclave.app.ui.lounge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.network.BundleRepository
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LoungeViewModel(
    application: Application,
    private val signalingClient: SignalingClient,
    private val database: com.enclave.app.data.local.EnclaveDatabase,
    private val partnerId: String,
    val myId: String,
    private val bundleRepository: BundleRepository? = null,
    private val vaultRepository: com.enclave.app.data.vault.VaultRepository? = null
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)

    // --- Status & Presence ---
    private val _myStatus = MutableStateFlow(ProfileStatus("❤️", "Online inside Enclave", 100, "Nothing playing", "12:00 PM"))
    val myStatus: StateFlow<ProfileStatus> = _myStatus.asStateFlow()

    private val _partnerStatus = MutableStateFlow(
        ProfileStatus("❤️", "Resting...", 100, "Offline", "12:00 PM")
    )
    val partnerStatus: StateFlow<ProfileStatus> = _partnerStatus.asStateFlow()

    private val _myBattery = MutableStateFlow("")
    val myBattery: StateFlow<String> = _myBattery.asStateFlow()

    private val _partnerBattery = MutableStateFlow("")
    val partnerBattery: StateFlow<String> = _partnerBattery.asStateFlow()

    private val _myWeather = MutableStateFlow("")
    val myWeather: StateFlow<String> = _myWeather.asStateFlow()

    private val _partnerWeather = MutableStateFlow("")
    val partnerWeather: StateFlow<String> = _partnerWeather.asStateFlow()

    // --- Countdown Widget Data ---
    val myCountdownLabel = MutableStateFlow("")
    val myCountdownTarget = MutableStateFlow(0L)
    val partnerCountdownLabel = MutableStateFlow("")
    val partnerCountdownTarget = MutableStateFlow(0L)

    // Heartbeat Rate Limiter
    private var lastHeartbeatSentTime = 0L

    // --- User profile states for side-by-side display ---
    val myProfile: StateFlow<com.enclave.app.data.local.UserProfileEntity?> = database.userProfileDao()
        .getMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partnerProfile: StateFlow<com.enclave.app.data.local.UserProfileEntity?> = database.userProfileDao()
        .getPartnerProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- Weather Sync Cache ---
    private var cachedWeather: Pair<Double, String>? = null
    private var lastWeatherFetchTime = 0L

    private val syncUseCase = LoungeSyncUseCase(signalingClient, myId, partnerId)

    init {
        // Load persistent countdown states
        val savedMyLabel = prefs.getString("my_countdown_label", "") ?: ""
        val savedMyTarget = prefs.getLong("my_countdown_target", 0L)
        myCountdownLabel.value = savedMyLabel
        myCountdownTarget.value = savedMyTarget

        val savedPartnerLabel = prefs.getString("partner_countdown_label", "") ?: ""
        val savedPartnerTarget = prefs.getLong("partner_countdown_target", 0L)
        partnerCountdownLabel.value = savedPartnerLabel
        partnerCountdownTarget.value = savedPartnerTarget

        refreshProfiles()
        syncMyStatus()
        observeSignaling()
    }

    private fun getBatteryPercentage(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = getApplication<Application>().registerReceiver(null, intentFilter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 100 else (level * 100 / scale.toFloat()).toInt()
    }

    private fun syncMyStatus() {
        viewModelScope.launch {
            val myProfileVal = bundleRepository?.fetchMyProfile()
            val city = myProfileVal?.locationCity.orEmpty()

            // Refresh weather every 15 minutes if city is set
            val now = System.currentTimeMillis()
            if (city.isNotEmpty() && (cachedWeather == null || now - lastWeatherFetchTime > 15 * 60 * 1000L)) {
                val weather = WeatherUseCase().fetchWeatherForCity(city)
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
                weatherCondition = cachedWeather?.second ?: "",
                countdownLabel = myCountdownLabel.value,
                countdownTarget = myCountdownTarget.value
            )
            val json = Json.encodeToString(_myStatus.value)
            sendLoungeMessage("LOUNGE_PROFILE_UPDATE", json)
        }
    }
    
    // --- Profile Management ---
    fun refreshProfiles() {
        // Automatically handled by database flows.
    }

    fun setCountdown(label: String, targetMillis: Long) {
        myCountdownLabel.value = label
        myCountdownTarget.value = targetMillis
        prefs.edit()
            .putString("my_countdown_label", label)
            .putLong("my_countdown_target", targetMillis)
            .apply()
        syncMyStatus()
    }

    fun updateCountdown(label: String, targetMillis: Long) {
        setCountdown(label, targetMillis)
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

    fun updateProfileLocation(city: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            repo.updateLocationCity(city)
            refreshProfiles()
            cachedWeather = null // Reset cache to force immediate reload
            syncMyStatus()
        }
    }

    fun exportSecureBackup(
        uri: android.net.Uri,
        passphrase: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val outputStream = resolver.openOutputStream(uri)
                    ?: throw Exception("Could not open output location")

                val result = com.enclave.app.data.local.BackupManager.exportBackup(getApplication(), outputStream, database, passphrase)

                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (result.isSuccess) onSuccess() else onFailure(result.exceptionOrNull() ?: Exception("Export failed"))
                }
            } catch (t: Throwable) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { onFailure(t) }
            }
        }
    }

    fun importSecureBackup(
        uri: android.net.Uri,
        passphrase: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val inputStream = resolver.openInputStream(uri)
                    ?: throw Exception("Could not open backup file")

                val result = com.enclave.app.data.local.BackupManager.importBackup(getApplication(), inputStream, database, passphrase)

                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (result.isSuccess) onSuccess() else onFailure(result.exceptionOrNull() ?: Exception("Import failed"))
                }
            } catch (t: Throwable) {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { onFailure(t) }
            }
        }
    }

    fun sendHeartbeat() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatSentTime < 2000) return
        lastHeartbeatSentTime = now
        sendLoungeMessage("LOUNGE_HEARTBEAT", "")
    }

    @Suppress("DEPRECATION")
    private fun triggerHeartbeatHaptic() {
        val context = getApplication<Application>().applicationContext
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 150, 100, 200)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

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
            syncUseCase.observeEvents().collect { event ->
                when (event) {
                    is LoungeIncomingEvent.Heartbeat -> triggerHeartbeatHaptic()
                    is LoungeIncomingEvent.ProfileUpdate -> {
                        _partnerStatus.value = event.status
                        prefs.edit()
                            .putString("partner_countdown_label", event.status.countdownLabel)
                            .putLong("partner_countdown_target", event.status.countdownTarget)
                            .apply()
                        partnerCountdownLabel.value = event.status.countdownLabel
                        partnerCountdownTarget.value = event.status.countdownTarget
                    }
                    is LoungeIncomingEvent.QuizCompleted -> {
                        refreshProfiles()
                    }
                    else -> {} // Handled by specialized viewmodels
                }
            }
        }
    }
}
