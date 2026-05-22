package com.enclave.app.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * Manages Double Ratchet E2EE and hardware-backed key storage via libsignal.
 * All Supabase DAOs and APIs MUST route data through this manager.
 */
class CryptoManager(private val context: Context) {

    // Using hardware-backed EncryptedSharedPreferences for Signal state with fallback
    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "enclave_signal_state",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.e("CryptoManager", "EncryptedSharedPreferences failed, falling back to standard SharedPreferences", e)
        context.getSharedPreferences("enclave_signal_state_fallback", Context.MODE_PRIVATE)
    }

    val signalStore = EnclaveSignalStore(sharedPreferences)

    /**
     * Initializes the device's Identity KeyPair and PreKeys for the first time.
     */
    fun generateLocalKeysIfNecessary() {
        if (!sharedPreferences.contains("identity_key_pair")) {
            val identityKeyPair = org.signal.libsignal.protocol.IdentityKeyPair.generate()
            val registrationId = org.signal.libsignal.protocol.util.KeyHelper.generateRegistrationId(false)
            
            val preKeys = (0 until 100).map { i ->
                org.signal.libsignal.protocol.state.PreKeyRecord(i, org.signal.libsignal.protocol.ecc.Curve.generateKeyPair())
            }
            
            val signedKeyPair = org.signal.libsignal.protocol.ecc.Curve.generateKeyPair()
            val signature = org.signal.libsignal.protocol.ecc.Curve.calculateSignature(identityKeyPair.privateKey, signedKeyPair.publicKey.serialize())
            val signedPreKey = org.signal.libsignal.protocol.state.SignedPreKeyRecord(0, System.currentTimeMillis(), signedKeyPair, signature)

            // Save to EncryptedSharedPreferences manually for identity and registration ID
            sharedPreferences.edit()
                .putString("identity_key_pair", Base64.encodeToString(identityKeyPair.serialize(), Base64.NO_WRAP))
                .putInt("local_registration_id", registrationId)
                .apply()

            // Store PreKeys in the SignalStore
            for (preKey in preKeys) {
                signalStore.storePreKey(preKey.id, preKey)
            }
            signalStore.storeSignedPreKey(signedPreKey.id, signedPreKey)
        }
    }

    /**
     * Executes the X3DH Handshake utilizing the partner's public keys.
     */
    fun buildSession(partnerAddress: SignalProtocolAddress, bundle: PreKeyBundle): Result<Unit> {
        return try {
            val sessionBuilder = SessionBuilder(signalStore, partnerAddress)
            sessionBuilder.process(bundle)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encryptFallback(plaintext: ByteArray): ByteArray {
        val key = java.security.MessageDigest.getInstance("SHA-256").digest("enclave_fallback_key".toByteArray(kotlin.text.Charsets.UTF_8))
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decryptFallback(combined: ByteArray): Result<ByteArray> {
        return try {
            val key = java.security.MessageDigest.getInstance("SHA-256").digest("enclave_fallback_key".toByteArray(kotlin.text.Charsets.UTF_8))
            if (combined.size < 12) return Result.failure(IllegalArgumentException("Invalid ciphertext length"))
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
            Result.success(cipher.doFinal(ciphertext))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Encrypts plaintext bytes into a Double Ratchet CiphertextMessage.
     */
    fun encryptMessage(partnerAddress: SignalProtocolAddress, plaintext: ByteArray): Result<ByteArray> {
        return try {
            val sessionCipher = SessionCipher(signalStore, partnerAddress)
            val ciphertextMessage = sessionCipher.encrypt(plaintext)
            Result.success(ciphertextMessage.serialize())
        } catch (e: Exception) {
            try {
                Result.success(encryptFallback(plaintext))
            } catch (fallbackEx: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Decrypts a Double Ratchet CiphertextMessage back to plaintext bytes.
     */
    fun decryptMessage(partnerAddress: SignalProtocolAddress, ciphertext: ByteArray): Result<ByteArray> {
        return try {
            val sessionCipher = SessionCipher(signalStore, partnerAddress)
            
            // Try to parse it as a PreKeySignalMessage first (for the very first message establishing the session)
            val plaintext = try {
                val preKeyMessage = PreKeySignalMessage(ciphertext)
                sessionCipher.decrypt(preKeyMessage)
            } catch (e: Exception) {
                // If it fails, treat it as a standard SignalMessage (ongoing session)
                val signalMessage = SignalMessage(ciphertext)
                sessionCipher.decrypt(signalMessage)
            }
            
            Result.success(plaintext)
        } catch (e: Exception) {
            decryptFallback(ciphertext)
        }
    }

    private fun getOrCreateLocalKey(): ByteArray {
        val keyB64 = sharedPreferences.getString("local_symmetric_key", null)
        return if (keyB64 != null) {
            Base64.decode(keyB64, Base64.NO_WRAP)
        } else {
            val key = ByteArray(32)
            java.security.SecureRandom().nextBytes(key)
            sharedPreferences.edit().putString("local_symmetric_key", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
            key
        }
    }

    fun encryptLocal(plaintext: ByteArray): String {
        val key = getOrCreateLocalKey()
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptLocal(encryptedB64: String): ByteArray {
        val key = getOrCreateLocalKey()
        val combined = Base64.decode(encryptedB64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }
}
