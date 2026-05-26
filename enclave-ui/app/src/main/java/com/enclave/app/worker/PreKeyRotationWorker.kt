package com.enclave.app.worker

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enclave.app.BuildConfig
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.network.BundleRepository
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.first

class PreKeyRotationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cryptoManager = CryptoManager(applicationContext)
            val signalStore = cryptoManager.signalStore

            // 1. Generate new Signed Pre-key
            val identityKeyPair = signalStore.identityKeyPair
            val existingSignedPreKeys = signalStore.loadSignedPreKeys()
            val newSignedPreKeyId = (existingSignedPreKeys.map { it.id }.maxOrNull() ?: 0) + 1
            
            val signedKeyPair = org.signal.libsignal.protocol.ecc.Curve.generateKeyPair()
            val signature = org.signal.libsignal.protocol.ecc.Curve.calculateSignature(
                identityKeyPair.privateKey, 
                signedKeyPair.publicKey.serialize()
            )
            val newSignedPreKey = org.signal.libsignal.protocol.state.SignedPreKeyRecord(
                newSignedPreKeyId, 
                System.currentTimeMillis(), 
                signedKeyPair, 
                signature
            )

            // Save the new signed prekey to store
            signalStore.storeSignedPreKey(newSignedPreKey.id, newSignedPreKey)

            // 2. Safely discard old signed prekeys (older than 14 days) to maintain forward secrecy
            val now = System.currentTimeMillis()
            existingSignedPreKeys.forEach { record ->
                if (record.id != newSignedPreKey.id && (now - record.timestamp > 14 * 24 * 3600 * 1000L)) {
                    signalStore.removeSignedPreKey(record.id)
                }
            }

            // 3. Replenish One-Time Prekeys if count is below 20
            val existingPreKeys = signalStore.loadPreKeys()
            if (existingPreKeys.size < 20) {
                val maxId = existingPreKeys.map { it.id }.maxOrNull() ?: 0
                val newPreKeys = (1..80).map { i ->
                    val id = maxId + i
                    org.signal.libsignal.protocol.state.PreKeyRecord(
                        id, 
                        org.signal.libsignal.protocol.ecc.Curve.generateKeyPair()
                    )
                }
                for (preKey in newPreKeys) {
                    signalStore.storePreKey(preKey.id, preKey)
                }
            }

            // 4. Initialize Supabase and upload new bundle
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
            }

            // BUG-15 Fix: Wait for the auth session to be restored from disk before uploading.
            // Without this, awaitAuth() in BundleRepository throws "Supabase is not authenticated"
            // and key rotation silently fails, degrading forward secrecy over time.
            supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
            if (supabase.auth.currentSessionOrNull() == null) {
                android.util.Log.w("PreKeyRotationWorker", "No active Supabase session — retrying key rotation later.")
                return Result.retry()
            }

            val bundleRepository = BundleRepository(supabase, signalStore, cryptoManager)
            bundleRepository.uploadLocalBundle()

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
            Result.retry()
        }
    }
}
