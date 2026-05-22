package com.enclave.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun serializePrefs(prefs: SharedPreferences): String {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            json.put(key, value)
        }
        return json.toString()
    }

    private fun deserializePrefs(prefs: SharedPreferences, jsonString: String) {
        val json = JSONObject(jsonString)
        val editor = prefs.edit()
        editor.clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = json.get(key)) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
            }
        }
        editor.apply()
    }

    suspend fun exportBackup(
        context: Context,
        outputStream: OutputStream,
        database: EnclaveDatabase,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Force WAL Checkpoint on disk to ensure database matches memory changes
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                cursor.moveToFirst()
            }

            // 2. Locate Database file
            val dbFile = context.getDatabasePath("enclave_secure.db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file does not exist"))
            }
            val dbBytes = dbFile.readBytes()

            // 3. Package SharedPreferences (Signal state & general app preferences)
            val signalPrefs = context.getSharedPreferences("enclave_signal_state", Context.MODE_PRIVATE)
            val appPrefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
            
            val prefsData = JSONObject().apply {
                put("signal_state", serializePrefs(signalPrefs))
                put("app_prefs", serializePrefs(appPrefs))
            }.toString()
            val prefsBytes = prefsData.toByteArray(Charsets.UTF_8)

            // 4. Assemble local plaintext binary structure
            val packetSize = 4 + prefsBytes.size + 4 + dbBytes.size
            val rawPacket = ByteBuffer.allocate(packetSize).apply {
                putInt(prefsBytes.size)
                put(prefsBytes)
                putInt(dbBytes.size)
                put(dbBytes)
            }.array()

            // 5. Generate secure salt and IV
            val salt = ByteArray(16)
            val iv = ByteArray(12)
            SecureRandom().apply {
                nextBytes(salt)
                nextBytes(iv)
            }

            // 6. Derive symmetric key and encrypt the packet
            val secretKey = deriveKeyFromPassphrase(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            
            val encryptedBytes = cipher.doFinal(rawPacket)

            // 7. Write combined headers and payload to public SAF Output Stream
            outputStream.use { out ->
                out.write(salt)
                out.write(iv)
                out.write(encryptedBytes)
                out.flush()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(
        context: Context,
        inputStream: InputStream,
        database: EnclaveDatabase,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Read entire file contents
            val combined = inputStream.use { it.readBytes() }
            if (combined.size <= 28) {
                return@withContext Result.failure(Exception("Invalid backup file format"))
            }

            // 2. Extract salt, IV, and ciphertext
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            // 3. Derive key and decrypt payload
            val secretKey = deriveKeyFromPassphrase(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)

            // 4. Unpack binary structure
            val buffer = ByteBuffer.wrap(decryptedBytes)
            
            val prefsSize = buffer.int
            val prefsBytes = ByteArray(prefsSize)
            buffer.get(prefsBytes)
            val prefsData = String(prefsBytes, Charsets.UTF_8)

            val dbSize = buffer.int
            val dbBytes = ByteArray(dbSize)
            buffer.get(dbBytes)

            // 5. Shutdown active database safely
            database.close()

            // 6. Overwrite the database file
            val dbFile = context.getDatabasePath("enclave_secure.db")
            dbFile.writeBytes(dbBytes)

            // 7. Clear existing WAL and SHM files
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) walFile.delete()
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) shmFile.delete()

            // 8. Restore SharedPreferences
            val prefsJson = JSONObject(prefsData)
            
            val signalPrefs = context.getSharedPreferences("enclave_signal_state", Context.MODE_PRIVATE)
            val appPrefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)

            deserializePrefs(signalPrefs, prefsJson.getString("signal_state"))
            deserializePrefs(appPrefs, prefsJson.getString("app_prefs"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
