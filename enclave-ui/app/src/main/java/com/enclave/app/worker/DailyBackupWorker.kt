package com.enclave.app.worker

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enclave.app.BuildConfig
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.vault.EncryptedFileManager
import com.enclave.app.network.BundleRepository
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DailyBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("DailyBackupWorker", "Starting automated daily backup process...")

        var tempZipFile: File? = null
        var tempEncFile: File? = null

        try {
            val database = EnclaveDatabase.getInstance(applicationContext)
            val encryptedFileManager = EncryptedFileManager(applicationContext)

            // 1. Force WAL checkpoint on disk to ensure database is updated
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    cursor.moveToFirst()
                }
            } catch (e: Exception) {
                Log.e("DailyBackupWorker", "WAL checkpoint failed", e)
            }

            // 2. Locate Database file
            val dbFile = applicationContext.getDatabasePath("enclave_secure.db")
            if (!dbFile.exists()) {
                Log.e("DailyBackupWorker", "Database file enclave_secure.db not found!")
                return@withContext Result.failure()
            }

            // 3. Create a temporary ZIP file
            tempZipFile = File(applicationContext.cacheDir, "temp_daily_backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(tempZipFile.outputStream().buffered()).use { zos ->
                // A. Add database
                zos.putNextEntry(ZipEntry("database/enclave_secure.db"))
                zos.write(dbFile.readBytes())
                zos.closeEntry()

                // B. Add preferences
                val signalPrefs = applicationContext.getSharedPreferences("enclave_signal_state", Context.MODE_PRIVATE)
                val appPrefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                val prefsJson = JSONObject().apply {
                    put("signal_state", serializePrefs(signalPrefs))
                    put("app_prefs", serializePrefs(appPrefs))
                }.toString()
                zos.putNextEntry(ZipEntry("preferences/prefs.json"))
                zos.write(prefsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // C. Add decrypted media files
                val vaultDir = File(applicationContext.filesDir, "vault")
                val files = vaultDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        try {
                            if (file.isFile && !file.name.startsWith("thumb_")) {
                                val decryptedBytes = decryptSecureFile(applicationContext, encryptedFileManager, file.name)
                                zos.putNextEntry(ZipEntry("media/${file.name}"))
                                zos.write(decryptedBytes)
                                zos.closeEntry()
                            }
                        } catch (e: Exception) {
                            Log.e("DailyBackupWorker", "Failed to package media file: ${file.name}", e)
                        }
                    }
                }
            }

            // 4. Retrieve passphrase
            val prefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
            val passphrase = prefs.getString("backup_passphrase", null)
                ?: prefs.getString("vault_key", null)
                ?: "default_enclave_backup_key"

            // 5. Generate secure salt and IV for the ZIP file encryption
            val salt = ByteArray(16)
            val iv = ByteArray(12)
            SecureRandom().apply {
                nextBytes(salt)
                nextBytes(iv)
            }

            // 6. Derive symmetric key and encrypt zip
            val secretKey = deriveKeyFromPassphrase(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val zipBytes = tempZipFile.readBytes()
            val encryptedBytes = cipher.doFinal(zipBytes)

            // Assemble encrypted output packet with salt + IV headers
            val finalPacket = ByteBuffer.allocate(salt.size + iv.size + encryptedBytes.size).apply {
                put(salt)
                put(iv)
                put(encryptedBytes)
            }.array()

            // 7. Initialize Supabase Client
            val cryptoManager = CryptoManager(applicationContext)
            val supabase = createSupabaseClient(
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
            }

            // Wait for session status flow to finish initial load of cached session
            supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }

            val myId = supabase.auth.currentUserOrNull()?.id
            if (myId == null) {
                Log.e("DailyBackupWorker", "User not authenticated in background — backup aborted.")
                return@withContext Result.failure()
            }

            // 8. Upload to backups bucket and clean rolling window
            val bundleRepository = BundleRepository(supabase, cryptoManager.signalStore, cryptoManager)
            val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = formatter.format(Date())
            val fileName = "backup_$dateStr.enc"

            bundleRepository.uploadBackupFile(fileName, finalPacket)
            bundleRepository.cleanOldBackups()

            Log.d("DailyBackupWorker", "Automated daily backup successfully completed & rolling window cleaned.")
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyBackupWorker", "Failed to complete daily automated backup", e)
            Result.retry()
        } finally {
            // Clean up temporary files safely
            try {
                tempZipFile?.let { if (it.exists()) it.delete() }
                tempEncFile?.let { if (it.exists()) it.delete() }
            } catch (ex: Exception) {
                android.util.Log.e("Enclave", "Exception caught", ex)
            }
        }
    }

    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun serializePrefs(prefs: android.content.SharedPreferences): String {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            json.put(key, value)
        }
        return json.toString()
    }

    private fun getSharedVaultKey(context: Context): ByteArray? {
        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val keyBase64 = prefs.getString("vault_key", null) ?: return null
        return try {
            Base64.decode(keyBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptSecureFile(context: Context, encryptedFileManager: EncryptedFileManager, fileName: String): ByteArray {
        val vaultKey = getSharedVaultKey(context)
        return if (vaultKey != null) {
            try {
                val rawFile = encryptedFileManager.getRawFile(fileName)
                val encryptedBytes = rawFile.readBytes()
                com.enclave.app.crypto.VaultCipher.decrypt(encryptedBytes, vaultKey)
            } catch (e: Exception) {
                val stream = encryptedFileManager.getSecureInputStream(fileName)
                stream.use { it.readBytes() }
            }
        } else {
            val stream = encryptedFileManager.getSecureInputStream(fileName)
            stream.use { it.readBytes() }
        }
    }
}
