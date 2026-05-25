package com.enclave.app.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Professional AES-GCM-256 file encryption utility for the Zero-Knowledge Collaborative Cloud Vault.
 */
object VaultCipher {

    private const val AES_KEY_SIZE_BYTES = 32
    private const val GCM_IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BITS = 128
    private const val ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Generates a secure, cryptographically random 256-bit AES key.
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(AES_KEY_SIZE_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Encrypts plain bytes using AES-GCM-256 and prepends the 12-byte random IV.
     */
    fun encrypt(plainBytes: ByteArray, keyBytes: ByteArray): ByteArray {
        require(keyBytes.size == AES_KEY_SIZE_BYTES) { "Invalid AES-256 key size" }

        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        val buffer = ByteBuffer.allocate(iv.size + ciphertext.size).apply {
            put(iv)
            put(ciphertext)
        }
        return buffer.array()
    }

    /**
     * Decrypts combined IV + Ciphertext bytes using AES-GCM-256.
     */
    fun decrypt(encryptedBytes: ByteArray, keyBytes: ByteArray): ByteArray {
        require(keyBytes.size == AES_KEY_SIZE_BYTES) { "Invalid AES-256 key size" }
        require(encryptedBytes.size > GCM_IV_SIZE_BYTES) { "Invalid encrypted payload size" }

        val iv = encryptedBytes.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val ciphertext = encryptedBytes.copyOfRange(GCM_IV_SIZE_BYTES, encryptedBytes.size)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
