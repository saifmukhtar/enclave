package com.enclave.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MIGRATION_7_8
import com.enclave.app.data.local.MIGRATION_8_9
import com.enclave.app.ui.call.CallLogScreen
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.data.vault.EncryptedFileManager
import com.enclave.app.media.MusicSyncController
import com.enclave.app.network.BundleRepository
import com.enclave.app.ui.auth.LoginScreen
import com.enclave.app.ui.chat.ChatScreen
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.kiss.KissWorkflowViewModel
import com.enclave.app.ui.kiss.KissViewModel
import com.enclave.app.ui.lounge.LoungeScreen
import com.enclave.app.ui.lounge.LoungeViewModel
import com.enclave.app.ui.profile.ProfileScreen
import com.enclave.app.ui.profile.ProfileViewModel
import com.enclave.app.ui.profile.StatusStoriesScreen
import com.enclave.app.ui.profile.StoryViewModel
import com.enclave.app.ui.vault.BiometricPromptManager
import com.enclave.app.ui.vault.VaultScreen
import com.enclave.app.ui.vault.VaultViewModel
import com.enclave.app.ui.call.CallState
import com.enclave.app.ui.call.CallViewModel
import com.enclave.app.ui.call.VideoCallScreen
import com.enclave.app.webrtc.SignalingClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), LifecycleEventObserver {

    private val autoLaunchKissState = mutableStateOf(false)
    private var vaultRepository: VaultRepository? = null
    private var callViewModel: CallViewModel? = null

    // Activity Result Launcher for Screen Capture permissions (MediaProjection API)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Android 14 requirement: Start foreground service of type mediaProjection BEFORE capture starts
            val serviceIntent = Intent(this, com.enclave.app.webrtc.ScreenShareService::class.java)
            startForegroundService(serviceIntent)

            // Feed MediaProjection intent data to CallViewModel
            callViewModel?.startScreenCapture(result.data!!)
        }
    }

    // Professional behavior:
    // - Debug builds: screenshots allowed
    // - Release builds: screenshots blocked via FLAG_SECURE
    private val blockScreenshots = !BuildConfig.DEBUG

    // Dynamic FLAG_SECURE controller
    fun setSecureMode(enabled: Boolean) {
        runOnUiThread {
            if (enabled && blockScreenshots) {
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.getBooleanExtra("AUTO_LAUNCH_KISS", false) == true) {
            autoLaunchKissState.value = true
        }
        
        // Enable secure mode by default (effective only when blockScreenshots is true).
        setSecureMode(true)
        
        lifecycle.addObserver(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val cryptoManager = remember { CryptoManager(this@MainActivity) }
                    
                    val supabase = remember {
                        createSupabaseClient(
                            supabaseUrl = BuildConfig.SUPABASE_URL,
                            supabaseKey = BuildConfig.SUPABASE_KEY
                        ) {
                            install(Auth)
                            install(Postgrest)
                            install(Storage)
                            install(Realtime)
                            
                            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(
                                kotlinx.serialization.json.Json {
                                    ignoreUnknownKeys = true
                                    isLenient         = true
                                    coerceInputValues = true
                                }
                            )
                            
                            requestTimeout = kotlin.time.Duration.parse("180s")
                        }
                    }

                    // --- Login and Session State ---
                    var isLoggedIn     by rememberSaveable { mutableStateOf(false) }
                    var loginError     by remember { mutableStateOf<String?>(null) }
                    var isLoginLoading by remember { mutableStateOf(false) }
                    var loggedInEmail  by rememberSaveable { mutableStateOf("") }
                    var resolvedMyId   by rememberSaveable { mutableStateOf("") }
                    var isSessionChecking by rememberSaveable { mutableStateOf(true) }

                    val scope = rememberCoroutineScope()

                    // Check for existing session on startup
                    LaunchedEffect(supabase) {
                        try {
                            supabase.auth.sessionStatus.collect { status ->
                                when (status) {
                                    is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> {
                                        val user = status.session.user
                                        if (user != null) {
                                            resolvedMyId = user.id
                                            loggedInEmail = user.email ?: ""
                                            
                                            // Save myId to SharedPreferences for background worker
                                            val prefs = this@MainActivity.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                                            prefs.edit().putString("my_id", user.id).apply()
                                            
                                            isLoggedIn = true
                                            isSessionChecking = false
                                            android.util.Log.d("SupabaseAuth", "Session authenticated: ${user.email} (id=${user.id})")
                                        }
                                    }
                                    is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> {
                                        isLoggedIn = false
                                        isSessionChecking = false
                                        android.util.Log.d("SupabaseAuth", "Session not authenticated")
                                    }
                                    else -> {
                                        // Loading/Initializing
                                        if (!isLoggedIn) {
                                            isSessionChecking = true
                                        }
                                        android.util.Log.d("SupabaseAuth", "Session initializing...")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SupabaseAuth", "Error restoring session", e)
                            isSessionChecking = false
                        }
                    }

                    if (isSessionChecking) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE598A7))
                        }
                        return@Surface
                    }

                    if (!isLoggedIn) {
                        LoginScreen(
                            isLoading    = isLoginLoading,
                            errorMessage = loginError,
                            onLoginSuccess = { enteredEmail, enteredPassword ->
                                isLoginLoading = true
                                loginError     = null
                                scope.launch {
                                    try {
                                        supabase.auth.signInWith(Email) {
                                            email    = enteredEmail
                                            password = enteredPassword
                                        }
                                        val user = supabase.auth.currentUserOrNull()
                                        val userId = user?.id ?: ""
                                        resolvedMyId  = userId
                                        loggedInEmail = enteredEmail
                                        
                                        // Save myId to SharedPreferences for background worker
                                        val prefs = this@MainActivity.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("my_id", userId).apply()
                                        
                                        android.util.Log.d("SupabaseAuth", "Signed in as $enteredEmail (id=$userId)")
                                        isLoggedIn     = true
                                        isLoginLoading = false
                                    } catch (e: Exception) {
                                        android.util.Log.e("SupabaseAuth", "Login failed", e)
                                        loginError     = "Invalid email or password. Please try again."
                                        isLoginLoading = false
                                    }
                                }
                            },
                            onSignUpSuccess = { enteredEmail, enteredPassword ->
                                isLoginLoading = true
                                loginError     = null
                                scope.launch {
                                    try {
                                        // Register via Supabase GoTrue Auth
                                        supabase.auth.signUpWith(Email) {
                                            email    = enteredEmail
                                            password = enteredPassword
                                        }
                                        // Perform auto-login immediately for a premium frictionless experience
                                        supabase.auth.signInWith(Email) {
                                            email    = enteredEmail
                                            password = enteredPassword
                                        }
                                        val user = supabase.auth.currentUserOrNull()
                                        val userId = user?.id ?: ""
                                        resolvedMyId  = userId
                                        loggedInEmail = enteredEmail
                                        
                                        val prefs = this@MainActivity.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("my_id", userId).apply()
                                        
                                        android.util.Log.d("SupabaseAuth", "Registered and signed in as $enteredEmail (id=$userId)")
                                        isLoggedIn     = true
                                        isLoginLoading = false
                                    } catch (e: Exception) {
                                        android.util.Log.e("SupabaseAuth", "Signup failed", e)
                                        loginError     = "Signup failed: ${e.localizedMessage ?: "Unknown error"}"
                                        isLoginLoading = false
                                    }
                                }
                            }
                        )
                        return@Surface
                    }

                    // --- App Lock Gate ---
                    val appLockManager = remember { BiometricPromptManager(this@MainActivity) }
                    val appAuthState by appLockManager.authState.collectAsState()

                    // Trigger biometric prompt automatically when the activity is active
                    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
                    
                    LaunchedEffect(appAuthState, lifecycleState) {
                        if (appAuthState == BiometricPromptManager.AuthState.LOCKED && 
                            lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            appLockManager.authenticate(
                                title = "Unlock Enclave",
                                subtitle = "Confirm fingerprint or PIN to unlock your private space"
                            )
                        }
                    }
                    
                    val bundleRepository = remember { BundleRepository(supabase, cryptoManager.signalStore, cryptoManager) }

                    val myId = resolvedMyId.ifBlank { "11111111-1111-1111-1111-111111111111" }
                    val prefs = remember { this@MainActivity.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE) }
                    var partnerId by remember(myId) {
                        val saved = prefs.getString("partner_id", "") ?: ""
                        mutableStateOf(saved.ifBlank {
                            if (myId == "11111111-1111-1111-1111-111111111111") {
                                "00000000-0000-0000-0000-000000000000"
                            } else {
                                "11111111-1111-1111-1111-111111111111"
                            }
                        })
                    }

                    LaunchedEffect(isLoggedIn, resolvedMyId) {
                        if (isLoggedIn && resolvedMyId.isNotBlank()) {
                            try {
                                // 1. Fetch our own profile to check if it has been provisioned
                                val myProfile = bundleRepository.fetchMyProfile()
                                if (myProfile == null) {
                                    val emailPrefix = loggedInEmail.substringBefore("@").lowercase().trim()
                                    val displayName = emailPrefix.replaceFirstChar { it.uppercase() }
                                    bundleRepository.uploadMyProfile(
                                        username = emailPrefix,
                                        displayName = displayName,
                                        bio = "Secure Enclave space",
                                        avatarUrl = ""
                                    )
                                    // Upload E2EE keys
                                    bundleRepository.uploadLocalBundle()
                                    android.util.Log.d("DynamicPairing", "Auto-provisioned profile & E2EE key bundle for $resolvedMyId")
                                }

                                // 2. Fetch all profiles to dynamically find the partner's UUID (deterministic whitelist matching)
                                val allProfiles = supabase.postgrest["profiles"]
                                    .select()
                                    .decodeList<com.enclave.app.network.UserProfile>()
                                
                                val peer = allProfiles.firstOrNull { it.id != resolvedMyId }

                                if (peer != null) {
                                    prefs.edit().putString("partner_id", peer.id).apply()
                                    partnerId = peer.id
                                    android.util.Log.d("DynamicPairing", "Resolved partnerId dynamically: ${peer.id} (username=${peer.username})")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DynamicPairing", "Verification / auto-pairing failed", e)
                            }
                        }
                    }
                    
                    val database = remember {
                        Room.databaseBuilder(
                            this@MainActivity.applicationContext,
                            EnclaveDatabase::class.java,
                            "enclave_db"
                        )
                        .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                        .fallbackToDestructiveMigration()
                        .build()
                    }
                    val encryptedFileManager = remember { EncryptedFileManager(this@MainActivity) }
                    val repo = remember { VaultRepository(encryptedFileManager, database.mediaMetadataDao()) }
                    this@MainActivity.vaultRepository = repo
                    
                    val vaultViewModel = remember { VaultViewModel(repo) }
                    val biometricManager = remember { BiometricPromptManager(this@MainActivity) }

                    val signalingClient = remember(myId) { SignalingClient(BuildConfig.SIGNALING_SERVER_URL, myId) }
                    
                    // Manage the WebSocket connection lifecycle dynamically based on app lifecycle state
                    DisposableEffect(signalingClient, resolvedMyId, lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (resolvedMyId.isNotBlank()) {
                                if (event == Lifecycle.Event.ON_START) {
                                    signalingClient.connect()
                                } else if (event == Lifecycle.Event.ON_STOP) {
                                    signalingClient.close() // pause: keeps HttpClient alive for reconnect
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        
                        // Immediate safe connect if the lifecycle is already started
                        if (resolvedMyId.isNotBlank() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            signalingClient.connect()
                        }

                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            signalingClient.destroy() // full teardown: also closes HttpClient
                        }
                    }

                    val chatViewModel = remember(myId, partnerId, signalingClient) {
                        ChatViewModel(
                            this@MainActivity.applicationContext,
                            bundleRepository,
                            cryptoManager,
                            cryptoManager.signalStore,
                            signalingClient,
                            repo,
                            database,
                            partnerId,
                            myId
                        )
                    }

                    var musicSyncController by remember { mutableStateOf<MusicSyncController?>(null) }
                    LaunchedEffect(myId, partnerId, signalingClient) {
                        val sessionToken = androidx.media3.session.SessionToken(
                            this@MainActivity,
                            android.content.ComponentName(this@MainActivity, com.enclave.app.media.MusicPlaybackService::class.java)
                        )
                        val controllerFuture = androidx.media3.session.MediaController.Builder(this@MainActivity, sessionToken).buildAsync()
                        controllerFuture.addListener({
                            try {
                                val controller = controllerFuture.get()
                                musicSyncController?.destroy() // cancel previous scope before replacing
                                musicSyncController = MusicSyncController(controller, signalingClient, myId, partnerId)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
                    }

                    val kissWorkflowViewModel = remember(myId, partnerId, signalingClient) {
                        KissWorkflowViewModel(
                            application = this@MainActivity.application,
                            signalingClient = signalingClient,
                            partnerId = partnerId,
                            myId = myId
                        )
                    }

                    val kissViewModel = remember(myId, partnerId, supabase) {
                        val roomId = if (myId < partnerId) "${myId}_${partnerId}" else "${partnerId}_${myId}"
                        KissViewModel.createDefault(
                            application = this@MainActivity.application,
                            supabase = supabase,
                            myId = myId,
                            partnerId = partnerId,
                            roomId = roomId
                        )
                    }

                    val callLogDao = remember { database.callLogDao() }
                    val callViewModel = remember(partnerId, signalingClient) {
                        CallViewModel(
                            application = this@MainActivity.application,
                            signalingClient = signalingClient,
                            partnerId = partnerId,
                            callLogDao = callLogDao
                        )
                    }
                    this@MainActivity.callViewModel = callViewModel

                    // Initialize LoungeViewModel for Phase 20 Intimacy Suite
                    val letterDao = remember { database.letterDao() }
                    val loungeViewModel = remember(myId, partnerId, signalingClient) {
                        LoungeViewModel(
                            application = this@MainActivity.application,
                            signalingClient = signalingClient,
                            cryptoManager = cryptoManager,
                            letterDao = letterDao,
                            database = database,
                            partnerId = partnerId,
                            myId = myId,
                            bundleRepository = bundleRepository
                        )
                    }

                    // Profile & Stories ViewModels
                    val profileViewModel = remember(myId, partnerId, signalingClient) {
                        ProfileViewModel(
                            application = this@MainActivity.application,
                            database = database,
                            bundleRepository = bundleRepository,
                            signalingClient = signalingClient,
                            cryptoManager = cryptoManager,
                            myId = myId,
                            partnerId = partnerId
                        )
                    }
                    val storyViewModel = remember(myId, partnerId, signalingClient) {
                        StoryViewModel(
                            application = this@MainActivity.application,
                            database = database,
                            cryptoManager = cryptoManager,
                            signalingClient = signalingClient,
                            myId = myId,
                            partnerId = partnerId
                        )
                    }

                    // Broadcast online status as soon as the WebSocket connection is established
                    // Using connectionState observer prevents the race where broadcastOnline() fires
                    // before the session is up and the message is silently dropped.
                    val connectionState by signalingClient.connectionState.collectAsState()
                    LaunchedEffect(connectionState) {
                        if (connectionState == SignalingClient.ConnectionState.CONNECTED) {
                            profileViewModel.broadcastOnline()
                        }
                    }

                    var currentTab by rememberSaveable { mutableStateOf("chat") }
                    var autoShowKissCanvas by remember { mutableStateOf(false) }
                    
                    val autoLaunchByIntent = autoLaunchKissState.value
                    LaunchedEffect(autoLaunchByIntent) {
                        if (autoLaunchByIntent) {
                            currentTab = "chat"
                            autoShowKissCanvas = true
                            autoLaunchKissState.value = false
                        }
                    }

                    // Audio/Video call permission launcher
                    val audioCallPermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        if (permissions[android.Manifest.permission.RECORD_AUDIO] == true) {
                            callViewModel.startCall("AUDIO")
                        }
                    }
                    val videoCallPermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val cameraOk = permissions[android.Manifest.permission.CAMERA] == true
                        val audioOk = permissions[android.Manifest.permission.RECORD_AUDIO] == true
                        if (cameraOk && audioOk) callViewModel.startCall("VIDEO")
                    }
                    var showProfileScreen by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        try {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val token = task.result
                                        android.util.Log.d("FCM_Token", "Retrieved FCM Token: $token")
                                        scope.launch {
                                            try {
                                                bundleRepository.syncFcmToken(token)
                                                android.util.Log.d("FCM_Token", "FCM token synced successfully with Supabase!")
                                            } catch (e: Exception) {
                                                android.util.Log.e("FCM_Token", "Failed to sync FCM token", e)
                                            }
                                        }
                                    } else {
                                        android.util.Log.e("FCM_Token", "Fetching FCM registration token failed", task.exception)
                                    }
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("FCM_Token", "Firebase Messaging initialization failed", e)
                        }
                    }

                    LaunchedEffect(Unit) {
                        chatViewModel.navigateToVault.collect {
                            currentTab = "vault"
                        }
                    }

                    // Modern Android 12+ Auto-Enter PiP Configuration & Trigger
                    val callState by callViewModel.callState.collectAsState()
                    LaunchedEffect(callState) {
                        if (callState == CallState.ACTIVE) {
                            val params = PictureInPictureParams.Builder()
                                .setAutoEnterEnabled(true)
                                .setAspectRatio(Rational(3, 4))
                                .build()
                            setPictureInPictureParams(params)
                        } else {
                            val params = PictureInPictureParams.Builder()
                                .setAutoEnterEnabled(false)
                                .build()
                            setPictureInPictureParams(params)
                        }
                    }

                    // Dynamic Screenshot Block (clear security when screen-sharing begins, restore instantly when it stops)
                    val isScreenSharing by callViewModel.isScreenSharing.collectAsState()
                    LaunchedEffect(isScreenSharing) {
                        if (isScreenSharing) {
                            setSecureMode(false) // Temporarily clear screenshots block to allow screen casting
                        } else {
                            setSecureMode(true) // Re-apply screenshots block to protect vault, chat, and letter data
                            val serviceIntent = Intent(this@MainActivity, com.enclave.app.webrtc.ScreenShareService::class.java)
                            stopService(serviceIntent)
                        }
                    }

                    // Screen Sharing Intent capturing triggers
                    LaunchedEffect(callViewModel) {
                        callViewModel.requestScreenShare.collect {
                            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        BackHandler(enabled = showProfileScreen || currentTab != "chat") {
                            if (showProfileScreen) {
                                showProfileScreen = false
                            } else if (currentTab != "chat") {
                                currentTab = "chat"
                            }
                        }
                        Scaffold(
                            bottomBar = {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                    NavigationBarItem(
                                        selected = currentTab == "chat",
                                        onClick = { currentTab = "chat" },
                                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat") },
                                        label = { Text("Chat") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == "calls",
                                        onClick = { currentTab = "calls" },
                                        icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                                        label = { Text("Calls") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == "stories",
                                        onClick = { currentTab = "stories" },
                                        icon = {
                                            val unviewed by storyViewModel.unviewedCount.collectAsState()
                                            BadgedBox(badge = {
                                                if (unviewed > 0) Badge { Text(unviewed.toString()) }
                                            }) {
                                                Icon(Icons.Default.AutoStories, contentDescription = "Stories")
                                            }
                                        },
                                        label = { Text("Status") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == "music",
                                        onClick = { currentTab = "music" },
                                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                                        label = { Text("Music") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == "vault",
                                        onClick = { currentTab = "vault" },
                                        icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                                        label = { Text("Vault") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == "lounge",
                                        onClick = { currentTab = "lounge" },
                                        icon = { Icon(Icons.Default.Casino, contentDescription = "Lounge") },
                                        label = { Text("Lounge") }
                                    )
                                }
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                when (currentTab) {
                                    "chat" -> ChatScreen(
                                        viewModel = chatViewModel,
                                        musicSyncController = musicSyncController,
                                        kissViewModel = kissViewModel,
                                        profileViewModel = profileViewModel,
                                        loungeViewModel = loungeViewModel,
                                        signalingClient = signalingClient,
                                        autoShowKissCanvas = autoShowKissCanvas,
                                        onKissCanvasClosed = { autoShowKissCanvas = false },
                                        onAudioCallClick = {
                                            val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                                this@MainActivity, android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasAudio) callViewModel.startCall("AUDIO")
                                            else audioCallPermissionsLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                                        },
                                        onVideoCallClick = {
                                            val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                                                this@MainActivity, android.Manifest.permission.CAMERA
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                                this@MainActivity, android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasCamera && hasAudio) callViewModel.startCall("VIDEO")
                                            else videoCallPermissionsLauncher.launch(arrayOf(
                                                android.Manifest.permission.CAMERA,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ))
                                        },
                                        onProfileClick = { showProfileScreen = true }
                                    )
                                    "calls" -> CallLogScreen(callLogDao)
                                    "stories" -> StatusStoriesScreen(storyViewModel)
                                    "music" -> com.enclave.app.ui.lounge.MusicLoungeTab(loungeViewModel, musicSyncController)
                                    "vault" -> VaultScreen(vaultViewModel, biometricManager, repo)
                                    else -> LoungeScreen(loungeViewModel, profileViewModel, musicSyncController)
                                }
                            }
                        }

                        // Glassmorphic In-App Notification Overlay for Mutual Intimacy
                        val partnerWaiting by kissWorkflowViewModel.partnerWaitingForKiss.collectAsState()
                        androidx.compose.animation.AnimatedVisibility(
                            visible = partnerWaiting,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Card(
                                onClick = {
                                    currentTab = "chat"
                                    autoShowKissCanvas = true
                                    kissWorkflowViewModel.clearPartnerWaitingForKiss()
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xE61E122C)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "❤️",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Column {
                                        Text(
                                            text = "Someone is thinking of you...",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Text(
                                            text = "Touch to join their warm presence",
                                            color = Color(0xFFE598A7),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        VideoCallScreen(callViewModel)

                        // Full-screen Profile editor overlay
                        if (showProfileScreen) {
                            ProfileScreen(
                                viewModel = profileViewModel,
                                onClose = { showProfileScreen = false }
                            )
                        }

                        // Secure Full-screen App Lock Overlay (drawn on top of all screens to prevent any interaction)
                        if (appAuthState != BiometricPromptManager.AuthState.UNLOCKED) {
                            com.enclave.app.ui.auth.AppLockScreen(
                                authState = appAuthState,
                                onUnlock = {
                                    appLockManager.authenticate(
                                        title = "Unlock Enclave",
                                        subtitle = "Confirm fingerprint or PIN to unlock your private space"
                                    )
                                },
                                onLogout = {
                                    scope.launch {
                                        try {
                                            supabase.auth.signOut()
                                            prefs.edit().remove("my_id").remove("partner_id").apply()
                                            resolvedMyId = ""
                                            isLoggedIn = false
                                        } catch (e: Exception) {
                                            android.util.Log.e("SupabaseAuth", "Sign out failed", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("AUTO_LAUNCH_KISS", false)) {
            autoLaunchKissState.value = true
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        callViewModel?.updatePiPState(isInPictureInPictureMode)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            vaultRepository?.clearMemoryCache()
        }
    }
}
