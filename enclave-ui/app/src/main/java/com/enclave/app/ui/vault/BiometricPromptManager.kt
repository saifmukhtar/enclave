package com.enclave.app.ui.vault

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BiometricPromptManager(
    private val activity: FragmentActivity
) : LifecycleEventObserver {

    companion object {
        @Volatile
        var isSystemPickerActive = false
    }

    enum class AuthState {
        LOCKED, AUTHENTICATING, UNLOCKED, ERROR
    }

    private val _authState = MutableStateFlow(AuthState.LOCKED)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        activity.lifecycle.addObserver(this)
        if (!canAuthenticate()) {
            _authState.value = AuthState.UNLOCKED
        }
    }

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val status = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        title: String = "Unlock Enclave",
        subtitle: String = "Confirm fingerprint or PIN to continue"
    ) {
        if (!canAuthenticate()) {
            _authState.value = AuthState.UNLOCKED
            return
        }
        if (_authState.value == AuthState.UNLOCKED) return
        _authState.value = AuthState.AUTHENTICATING

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _authState.value = AuthState.UNLOCKED
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _authState.value = AuthState.ERROR
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    _authState.value = AuthState.LOCKED
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun lock() {
        if (isSystemPickerActive) {
            android.util.Log.d("BiometricPrompt", "Bypassing biometric lock because system picker is active")
            return
        }
        if (canAuthenticate()) {
            _authState.value = AuthState.LOCKED
        } else {
            _authState.value = AuthState.UNLOCKED
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            // Instantly reset the UI state to Locked when the app goes background
            lock()
        } else if (event == Lifecycle.Event.ON_RESUME) {
            // Reset the system picker flag when returning to foreground
            isSystemPickerActive = false
        }
    }
}
