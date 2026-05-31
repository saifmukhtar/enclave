package dev.saifmukhtar.enclave.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ConfigEncryptor {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_DERIVATION_ITERATIONS = 2000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(plainText: String, pin: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(pin.toCharArray(), salt, KEY_DERIVATION_ITERATIONS, KEY_LENGTH_BITS)
        val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)

        return "$saltBase64:$ivBase64:$cipherTextBase64"
    }

    fun decrypt(encryptedPayload: String, pin: String): String {
        val parts = encryptedPayload.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid encrypted payload format")
        }

        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)

        val keySpec = PBEKeySpec(pin.toCharArray(), salt, KEY_DERIVATION_ITERATIONS, KEY_LENGTH_BITS)
        val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val decryptedBytes = cipher.doFinal(cipherText)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}
