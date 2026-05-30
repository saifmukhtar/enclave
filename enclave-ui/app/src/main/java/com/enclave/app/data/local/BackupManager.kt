package com.enclave.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "enclave_signal_state",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private const val ITERATIONS_LEGACY = 10000
    private const val ITERATIONS_V2 = 600000
    private const val KEY_LENGTH = 256
    private val MAGIC_HEADER = byteArrayOf('E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), 'V'.code.toByte())

    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH)
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
            val dbFile = context.getDatabasePath("enclave_db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file does not exist"))
            }
            val dbBytes = dbFile.readBytes()

            // 3. Package SharedPreferences (Signal state & general app preferences)
            val signalPrefs = try {
                getEncryptedPrefs(context)
            } catch (e: Exception) {
                context.getSharedPreferences("enclave_signal_state", Context.MODE_PRIVATE)
            }
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
            val secretKey = deriveKeyFromPassphrase(passphrase, salt, ITERATIONS_V2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            
            val encryptedBytes = cipher.doFinal(rawPacket)

            // 7. Write combined headers and payload to public SAF Output Stream
            outputStream.use { out ->
                out.write(MAGIC_HEADER)
                out.write(2) // Version 2
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

            // 2. Check for magic header and version
            val isV2 = combined.size > 5 + 28 && 
                    combined[0] == MAGIC_HEADER[0] &&
                    combined[1] == MAGIC_HEADER[1] &&
                    combined[2] == MAGIC_HEADER[2] &&
                    combined[3] == MAGIC_HEADER[3]

            val (iterations, offset) = if (isV2) {
                val version = combined[4].toInt()
                if (version == 2) {
                    Pair(ITERATIONS_V2, 5)
                } else {
                    return@withContext Result.failure(Exception("Unsupported backup version: $version"))
                }
            } else {
                Pair(ITERATIONS_LEGACY, 0)
            }

            // 3. Extract salt, IV, and ciphertext
            val salt = combined.copyOfRange(offset, offset + 16)
            val iv = combined.copyOfRange(offset + 16, offset + 28)
            val ciphertext = combined.copyOfRange(offset + 28, combined.size)

            // 4. Derive key and decrypt payload
            val secretKey = deriveKeyFromPassphrase(passphrase, salt, iterations)
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
            val dbFile = context.getDatabasePath("enclave_db")
            dbFile.writeBytes(dbBytes)

            // 7. Clear existing WAL and SHM files
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) walFile.delete()
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) shmFile.delete()

            // 8. Restore SharedPreferences
            val prefsJson = JSONObject(prefsData)
            
            val signalPrefs = try {
                getEncryptedPrefs(context)
            } catch (e: Exception) {
                context.getSharedPreferences("enclave_signal_state", Context.MODE_PRIVATE)
            }
            val appPrefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)

            deserializePrefs(signalPrefs, prefsJson.getString("signal_state"))
            deserializePrefs(appPrefs, prefsJson.getString("app_prefs"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
