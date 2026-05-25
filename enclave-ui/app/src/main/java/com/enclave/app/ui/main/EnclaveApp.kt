package com.enclave.app.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.enclave.app.BuildConfig
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.vault.EncryptedFileManager
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.media.MusicSyncController
import com.enclave.app.network.BundleRepository
import com.enclave.app.ui.auth.AppLockScreen
import com.enclave.app.ui.auth.LoginScreen
import com.enclave.app.ui.call.CallViewModel
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.kiss.KissViewModel
import com.enclave.app.ui.kiss.KissWorkflowViewModel
import com.enclave.app.ui.lounge.LoungeViewModel
import com.enclave.app.ui.profile.ProfileViewModel
import com.enclave.app.ui.profile.StoryViewModel
import com.enclave.app.ui.vault.BiometricPromptManager
import com.enclave.app.ui.vault.VaultViewModel
import com.enclave.app.webrtc.SignalingClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.launch

@Composable
fun EnclaveApp(
    autoLaunchKissState: Boolean,
    onKissCanvasClosed: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val application = activity.application
    val context = activity.applicationContext
    val scope = rememberCoroutineScope()

    val cryptoManager = remember { CryptoManager(activity) }

    val supabase = remember {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            httpEngine = io.ktor.client.engine.okhttp.OkHttp.create {
                 config {
                    connectTimeout(java.time.Duration.ofMinutes(5))
                    readTimeout(java.time.Duration.ofMinutes(5))
                    writeTimeout(java.time.Duration.ofMinutes(5))
                    val parsedHost = try {
                        java.net.URI(BuildConfig.SUPABASE_URL).host
                    } catch (e: Exception) {
                        null
                    }
                    if (parsedHost != null && !parsedHost.replace(".", "").all { it.isDigit() } && parsedHost != "localhost") {
                        val pinner = okhttp3.CertificatePinner.Builder()
                            .add("*.$parsedHost", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                            .add(parsedHost, "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                            .build()
                        certificatePinner(pinner)
                    }
                }
            }
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

    LaunchedEffect(supabase) {
        try {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user != null) {
                            resolvedMyId = user.id
                            loggedInEmail = user.email ?: ""
                            val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("my_id", user.id).apply()
                            isLoggedIn = true
                            isSessionChecking = false
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        isLoggedIn = false
                        isSessionChecking = false
                    }
                    else -> {
                        if (!isLoggedIn) {
                            isSessionChecking = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isSessionChecking = false
        }
    }

    if (isSessionChecking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE598A7))
        }
        return
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
                            email    = enteredEmail.trim().lowercase()
                            password = enteredPassword
                        }
                        val user = supabase.auth.currentUserOrNull()
                        val userId = user?.id ?: ""
                        resolvedMyId  = userId
                        loggedInEmail = enteredEmail
                        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("my_id", userId).apply()
                        isLoggedIn     = true
                        isLoginLoading = false
                    } catch (e: Exception) {
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
                        supabase.auth.signUpWith(Email) {
                            email    = enteredEmail.trim().lowercase()
                            password = enteredPassword
                        }
                        supabase.auth.signInWith(Email) {
                            email    = enteredEmail.trim().lowercase()
                            password = enteredPassword
                        }
                        val user = supabase.auth.currentUserOrNull()
                        val userId = user?.id ?: ""
                        resolvedMyId  = userId
                        loggedInEmail = enteredEmail
                        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("my_id", userId).apply()
                        isLoggedIn     = true
                        isLoginLoading = false
                    } catch (e: Exception) {
                        loginError     = "Signup failed: ${e.localizedMessage ?: "Unknown error"}"
                        isLoginLoading = false
                    }
                }
            }
        )
        return
    }

    // --- App Lock Gate ---
    val appLockManager = remember { BiometricPromptManager(activity) }
    val appAuthState by appLockManager.authState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(appAuthState, lifecycleState) {
        if (appAuthState == BiometricPromptManager.AuthState.LOCKED && 
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            appLockManager.authenticate(
                title = "Unlock Enclave",
                subtitle = "Confirm fingerprint or PIN to unlock your private space"
            )
        }
    }

    val bundleRepository = remember { BundleRepository(supabase, cryptoManager.signalStore, cryptoManager) }
    val myId = resolvedMyId.ifBlank { "11111111-1111-1111-1111-111111111111" }
    val prefs = remember { context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE) }
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
                    bundleRepository.uploadLocalBundle()
                }

                val savedPartner = prefs.getString("partner_id", "")
                if (savedPartner.isNullOrBlank() || savedPartner == "11111111-1111-1111-1111-111111111111" || savedPartner == "00000000-0000-0000-0000-000000000000") {
                    val allProfiles = supabase.postgrest["profiles"]
                        .select()
                        .decodeList<com.enclave.app.network.UserProfile>()
                    val peer = allProfiles.firstOrNull { it.id != resolvedMyId }
                    if (peer != null) {
                        prefs.edit().putString("partner_id", peer.id).apply()
                        partnerId = peer.id
                    }
                }
            } catch (e: Exception) {
                // handle
            }
        }
    }

    val database = remember { EnclaveDatabase.getInstance(context) }
    val encryptedFileManager = remember { EncryptedFileManager(activity) }
    val vaultRepository = remember { VaultRepository(context, encryptedFileManager, database.mediaMetadataDao(), bundleRepository) }
    val vaultViewModel = remember { VaultViewModel(vaultRepository) }

    val signalingClient = remember(myId) {
        SignalingClient(
            url = BuildConfig.SIGNALING_SERVER_URL,
            myId = myId,
            tokenProvider = { supabase.auth.currentSessionOrNull()?.accessToken }
        )
    }

    DisposableEffect(signalingClient, resolvedMyId, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (resolvedMyId.isNotBlank()) {
                if (event == Lifecycle.Event.ON_START) {
                    signalingClient.connect()
                } else if (event == Lifecycle.Event.ON_STOP) {
                    signalingClient.close()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (resolvedMyId.isNotBlank() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            signalingClient.connect()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            signalingClient.destroy()
        }
    }

    val chatViewModel = remember(myId, partnerId, signalingClient) {
        ChatViewModel(
            context,
            bundleRepository,
            cryptoManager,
            cryptoManager.signalStore,
            signalingClient,
            vaultRepository,
            database,
            partnerId,
            myId
        )
    }

    var musicSyncController by remember { mutableStateOf<MusicSyncController?>(null) }
    LaunchedEffect(myId, partnerId, signalingClient) {
        val sessionToken = androidx.media3.session.SessionToken(
            activity,
            android.content.ComponentName(activity, com.enclave.app.media.MusicPlaybackService::class.java)
        )
        val controllerFuture = androidx.media3.session.MediaController.Builder(activity, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                musicSyncController?.destroy()
                musicSyncController = MusicSyncController(controller, signalingClient, myId, partnerId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }

    val kissWorkflowViewModel = remember(myId, partnerId, signalingClient) {
        KissWorkflowViewModel(
            application = application,
            signalingClient = signalingClient,
            partnerId = partnerId,
            myId = myId
        )
    }

    val kissViewModel = remember(myId, partnerId, supabase) {
        val roomId = if (myId < partnerId) "${myId}_${partnerId}" else "${partnerId}_${myId}"
        KissViewModel.createDefault(
            application = application,
            supabase = supabase,
            myId = myId,
            partnerId = partnerId,
            roomId = roomId
        )
    }

    val callLogDao = remember { database.callLogDao() }
    val callViewModel = remember(partnerId, signalingClient) {
        CallViewModel(
            application = application,
            signalingClient = signalingClient,
            partnerId = partnerId,
            callLogDao = callLogDao
        )
    }

    val letterDao = remember { database.letterDao() }
    val loungeViewModel = remember(myId, partnerId, signalingClient) {
        LoungeViewModel(
            application = application,
            signalingClient = signalingClient,
            cryptoManager = cryptoManager,
            letterDao = letterDao,
            database = database,
            partnerId = partnerId,
            myId = myId,
            bundleRepository = bundleRepository,
            vaultRepository = vaultRepository
        )
    }

    val profileViewModel = remember(myId, partnerId, signalingClient) {
        ProfileViewModel(
            application = application,
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
            application = application,
            database = database,
            cryptoManager = cryptoManager,
            signalingClient = signalingClient,
            myId = myId,
            partnerId = partnerId
        )
    }

    val connectionState by signalingClient.connectionState.collectAsState()
    LaunchedEffect(connectionState) {
        if (connectionState == SignalingClient.ConnectionState.CONNECTED) {
            profileViewModel.broadcastOnline()
        }
    }

    LaunchedEffect(resolvedMyId) {
        if (resolvedMyId.isNotBlank() && resolvedMyId != "11111111-1111-1111-1111-111111111111") {
            try {
                val topic = resolvedMyId
                prefs.edit().putString("ntfy_topic", topic).apply()
                
                scope.launch {
                    try {
                        bundleRepository.syncNtfyPushToken(topic)
                        val serviceIntent = Intent(activity, com.enclave.app.notifications.NtfyListenerService::class.java)
                        activity.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        // handle
                    }
                }
            } catch (e: Exception) {
                // handle
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        EnclaveMainScreen(
            chatViewModel = chatViewModel,
            kissViewModel = kissViewModel,
            kissWorkflowViewModel = kissWorkflowViewModel,
            callViewModel = callViewModel,
            loungeViewModel = loungeViewModel,
            profileViewModel = profileViewModel,
            storyViewModel = storyViewModel,
            vaultViewModel = vaultViewModel,
            biometricManager = appLockManager,
            vaultRepository = vaultRepository,
            signalingClient = signalingClient,
            musicSyncController = musicSyncController,
            autoLaunchKissState = autoLaunchKissState,
            onKissCanvasClosed = onKissCanvasClosed
        )

        // Secure Full-screen App Lock Overlay (drawn on top of all screens to prevent any interaction)
        if (appAuthState != BiometricPromptManager.AuthState.UNLOCKED) {
            AppLockScreen(
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
                            // handle
                        }
                    }
                }
            )
        }
    }
}
