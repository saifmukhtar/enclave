@file:Suppress("DEPRECATION")
package dev.saifmukhtar.enclave.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

@Suppress("DEPRECATION")
class ConfigManager private constructor(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "enclave_secure_config",
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context).also { INSTANCE = it }
            }
        }
    }

    fun isConfigured(): Boolean {
        return !getSupabaseUrl().isNullOrBlank() && 
               !getSupabaseKey().isNullOrBlank() && 
               !getSignalingServerUrl().isNullOrBlank()
    }

    fun saveConfig(
        supabaseUrl: String,
        supabaseKey: String,
        signalingUrl: String,
        turnUrl: String,
        turnUser: String,
        turnPass: String,
        ntfyUrl: String,
        ntfyUser: String,
        ntfyPass: String
    ) {
        prefs.edit().apply {
            putString("supabase_url", supabaseUrl.trim())
            putString("supabase_key", supabaseKey.trim())
            putString("signaling_server_url", signalingUrl.trim())
            putString("turn_server_url", turnUrl.trim())
            putString("turn_username", turnUser.trim())
            putString("turn_password", turnPass.trim())
            putString("ntfy_server_url", ntfyUrl.trim())
            putString("ntfy_username", ntfyUser.trim())
            putString("ntfy_password", ntfyPass.trim())
            apply()
        }
    }

    fun getSupabaseUrl(): String? = prefs.getString("supabase_url", null)
    fun getSupabaseKey(): String? = prefs.getString("supabase_key", null)
    fun getSignalingServerUrl(): String? = prefs.getString("signaling_server_url", null)
    fun getTurnServerUrl(): String? = prefs.getString("turn_server_url", null)
    fun getTurnUsername(): String? = prefs.getString("turn_username", null)
    fun getTurnPassword(): String? = prefs.getString("turn_password", null)
    fun getNtfyServerUrl(): String? = prefs.getString("ntfy_server_url", null)
    fun getNtfyUsername(): String? = prefs.getString("ntfy_username", null)
    fun getNtfyPassword(): String? = prefs.getString("ntfy_password", null)

    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}
