package dev.saifmukhtar.enclave.ui.lounge

import kotlinx.serialization.json.Json


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.crypto.CryptoManager
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase
import dev.saifmukhtar.enclave.data.local.UserProfileEntity
import dev.saifmukhtar.enclave.network.BundleRepository
import dev.saifmukhtar.enclave.webrtc.LenientJson
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoungeGamesViewModel(
    private val signalingClient: SignalingClient,
    private val cryptoManager: CryptoManager,
    private val database: EnclaveDatabase,
    private val bundleRepository: BundleRepository?,
    private val partnerId: String,
    val myId: String,
    private val loungeSyncUseCase: LoungeSyncUseCase
) : ViewModel() {

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

    // --- Scratch-to-reveal Custom Photos ---
    private val _scratchState = MutableStateFlow<ScratchState?>(null)
    val scratchState: StateFlow<ScratchState?> = _scratchState.asStateFlow()

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

    // --- User profile states for Love Language quiz side-by-side display ---
    val myProfile: StateFlow<UserProfileEntity?> = database.userProfileDao()
        .getMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partnerProfile: StateFlow<UserProfileEntity?> = database.userProfileDao()
        .getPartnerProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            loungeSyncUseCase.observeEvents().collect { event ->
                when (event) {
                    is LoungeIncomingEvent.DiceRoll -> {
                        _diceValue.value = event.event.rolledValue
                        _isDiceRolling.value = true
                        _diceTickerEvent.tryEmit(Unit)
                        kotlinx.coroutines.delay(1000)
                        _isDiceRolling.value = false
                    }
                    is LoungeIncomingEvent.TruthOrDare -> {
                        _currentPrompt.value = event.event.prompt
                        _isTruthSelected.value = event.event.isTruth
                    }
                    is LoungeIncomingEvent.ScratchUpload -> {
                        _scratchState.value = if (event.bytes != null) ScratchState(event.bytes, isSender = false, isSeen = false) else null
                    }
                    is LoungeIncomingEvent.ScratchSeen -> {
                        _scratchState.value = _scratchState.value?.copy(isSeen = true)
                    }
                    is LoungeIncomingEvent.ScratchDestroyed -> {
                        _scratchState.value = _scratchState.value?.copy(isDestroyed = true)
                    }
                    is LoungeIncomingEvent.QuizCompleted -> {
                        refreshProfiles()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun refreshProfiles() {
        // Automatically handled by database flows.
    }

    fun sendLoungeMessage(type: String, payload: String) {
        val msg = SignalMessageWrapper(
            senderId = myId,
            type = type,
            payload = payload
        )
        val jsonStr = Json.encodeToString(SignalMessageWrapper.serializer(), msg)
        val encryptedResult = cryptoManager.encryptMessage(
            org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1),
            jsonStr.toByteArray(Charsets.UTF_8)
        )
        if (encryptedResult.isSuccess) {
            viewModelScope.launch {
                signalingClient.sendEncryptedMessage(partnerId, encryptedResult.getOrThrow(), "LOUNGE")
            }
        }
    }

    fun sendScratchImage(bytes: ByteArray) {
        _scratchState.value = ScratchState(bytes, isSender = true, isSeen = false)
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        sendLoungeMessage("LOUNGE_SCRATCH_UPLOAD", base64)
    }

    fun clearScratchImage() {
        _scratchState.value = null
        sendLoungeMessage("LOUNGE_SCRATCH_UPLOAD", "")
    }

    fun notifyScratchSeen() {
        _scratchState.value = _scratchState.value?.copy(isSeen = true)
        sendLoungeMessage("LOUNGE_SCRATCH_SEEN", "")
    }

    fun notifyScratchDestroyed() {
        _scratchState.value = _scratchState.value?.copy(isDestroyed = true)
        sendLoungeMessage("LOUNGE_SCRATCH_DESTROYED", "")
    }

    fun rollDice() {
        viewModelScope.launch {
            _isDiceRolling.value = true
            _diceTickerEvent.tryEmit(Unit)
            kotlinx.coroutines.delay(1000)
            val finalVal = (1..6).random()
            _diceValue.value = finalVal
            _isDiceRolling.value = false
            
            val event = SyncedDiceEvent(rolledValue = finalVal, seed = System.currentTimeMillis())
            sendLoungeMessage("LOUNGE_DICE_ROLL", Json.encodeToString(SyncedDiceEvent.serializer(), event))
        }
    }

    fun pickTruthOrDareCard(isTruth: Boolean) {
        val pool = if (isTruth) {
            listOf(
                "What's your biggest fear in this relationship?",
                "When did you realize you were in love with me?",
                "What is a secret you've never told me?",
                "What is your favorite memory of us?",
                "If you could change one thing about our relationship, what would it be?"
            )
        } else {
            listOf(
                "Give me a 1-minute massage.",
                "Send me a voice memo of you singing a love song.",
                "Draw a quick picture of us and share it.",
                "Let me pick the movie tonight.",
                "Kiss me passionately for 30 seconds."
            )
        }
        val prompt = pool.random()
        _currentPrompt.value = prompt
        _isTruthSelected.value = isTruth

        val promptId = kotlin.random.Random.nextInt(100)
        val event = SyncedTruthOrDareEvent(cardIndex = promptId, isTruth = isTruth, prompt = prompt)
        sendLoungeMessage("LOUNGE_TRUTH_OR_DARE", Json.encodeToString(SyncedTruthOrDareEvent.serializer(), event))
    }

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

    class Factory(
        private val signalingClient: SignalingClient,
        private val cryptoManager: CryptoManager,
        private val database: EnclaveDatabase,
        private val bundleRepository: BundleRepository?,
        private val partnerId: String,
        private val myId: String,
        private val loungeSyncUseCase: LoungeSyncUseCase
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoungeGamesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoungeGamesViewModel(signalingClient, cryptoManager, database, bundleRepository, partnerId, myId, loungeSyncUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
