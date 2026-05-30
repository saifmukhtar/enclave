package com.enclave.app.crypto

import android.content.SharedPreferences
import android.util.Base64
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.concurrent.ConcurrentHashMap

class EnclaveSignalStore(
    private val prefs: SharedPreferences
) : SignalProtocolStore {

    // In-memory caches for fast Double Ratchet reads/writes (to prevent UI stutter)
    private val sessionCache = ConcurrentHashMap<String, ByteArray>()
    private val preKeyCache = ConcurrentHashMap<Int, ByteArray>()
    private val signedPreKeyCache = ConcurrentHashMap<Int, ByteArray>()

    private var preKeyIdsCache: MutableSet<String>? = null
    private var signedPreKeyIdsCache: MutableSet<String>? = null
    private var sessionKeysCache: MutableSet<String>? = null

    // -- IdentityKeyStore --

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val serialized = prefs.getString("identity_key_pair", null)
            ?: throw IllegalStateException("IdentityKeyPair not generated")
        return IdentityKeyPair(Base64.decode(serialized, Base64.NO_WRAP))
    }

    override fun getLocalRegistrationId(): Int {
        return prefs.getInt("local_registration_id", 0)
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        return prefs.edit().putString("identity_${address.name}", Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)).commit()
    }

    override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
        val serialized = Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)
        val trusted = prefs.getString("identity_${address.name}", null)
        return when {
            trusted == null -> {
                prefs.edit().putString("identity_${address.name}", serialized).commit()
                true
            }
            trusted == serialized -> true
            else -> {
                android.util.Log.e("EnclaveSignalStore", "IDENTITY KEY MISMATCH for ${address.name}. Possible MITM attack. Rejecting.")
                false
            }
        }
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val serialized = prefs.getString("identity_${address.name}", null) ?: return null
        return IdentityKey(Base64.decode(serialized, Base64.NO_WRAP), 0)
    }

    // -- SessionStore --

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val key = "session_${address.name}_${address.deviceId}"
        val serialized = sessionCache[key] ?: prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        return if (serialized != null) SessionRecord(serialized) else SessionRecord()
    }

    @Synchronized
    private fun getPreKeyIds(): Set<String> {
        if (preKeyIdsCache != null) return preKeyIdsCache!!
        val ids = prefs.getStringSet("prekey_ids_list", null)
        if (ids != null) {
            preKeyIdsCache = ids.toMutableSet()
            return preKeyIdsCache!!
        }
        
        // One-time fallback/migration if key list doesn't exist
        val migrated = mutableSetOf<String>()
        for (i in 0..150) {
            if (prefs.contains("prekey_$i")) {
                migrated.add(i.toString())
            }
        }
        if (migrated.isNotEmpty()) {
            prefs.edit().putStringSet("prekey_ids_list", migrated).apply()
        }
        preKeyIdsCache = migrated
        return migrated
    }

    @Synchronized
    private fun addPreKeyId(id: Int) {
        val current = getPreKeyIds().toMutableSet()
        if (current.add(id.toString())) {
            preKeyIdsCache = current
            prefs.edit().putStringSet("prekey_ids_list", current).commit()
        }
    }

    @Synchronized
    private fun removePreKeyId(id: Int) {
        val current = getPreKeyIds().toMutableSet()
        if (current.remove(id.toString())) {
            preKeyIdsCache = current
            prefs.edit().putStringSet("prekey_ids_list", current).commit()
        }
    }

    @Synchronized
    private fun getSignedPreKeyIds(): Set<String> {
        if (signedPreKeyIdsCache != null) return signedPreKeyIdsCache!!
        val ids = prefs.getStringSet("signed_prekey_ids_list", null)
        if (ids != null) {
            signedPreKeyIdsCache = ids.toMutableSet()
            return signedPreKeyIdsCache!!
        }
        
        // One-time fallback/migration if key list doesn't exist
        val migrated = mutableSetOf<String>()
        for (i in 0..20) {
            if (prefs.contains("signed_prekey_$i")) {
                migrated.add(i.toString())
            }
        }
        if (migrated.isNotEmpty()) {
            prefs.edit().putStringSet("signed_prekey_ids_list", migrated).commit()
        }
        signedPreKeyIdsCache = migrated
        return migrated
    }

    @Synchronized
    private fun addSignedPreKeyId(id: Int) {
        val current = getSignedPreKeyIds().toMutableSet()
        if (current.add(id.toString())) {
            signedPreKeyIdsCache = current
            prefs.edit().putStringSet("signed_prekey_ids_list", current).commit()
        }
    }

    @Synchronized
    private fun removeSignedPreKeyId(id: Int) {
        val current = getSignedPreKeyIds().toMutableSet()
        if (current.remove(id.toString())) {
            signedPreKeyIdsCache = current
            prefs.edit().putStringSet("signed_prekey_ids_list", current).commit()
        }
    }

    @Synchronized
    private fun getSessionKeys(): Set<String> {
        if (sessionKeysCache != null) return sessionKeysCache!!
        val keys = prefs.getStringSet("session_keys_list", null) ?: emptySet()
        sessionKeysCache = keys.toMutableSet()
        return sessionKeysCache!!
    }

    @Synchronized
    private fun addSessionKey(key: String) {
        val current = getSessionKeys().toMutableSet()
        if (current.add(key)) {
            sessionKeysCache = current
            prefs.edit().putStringSet("session_keys_list", current).commit()
        }
    }

    @Synchronized
    private fun removeSessionKey(key: String) {
        val current = getSessionKeys().toMutableSet()
        if (current.remove(key)) {
            sessionKeysCache = current
            prefs.edit().putStringSet("session_keys_list", current).commit()
        }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val key = "session_${address.name}_${address.deviceId}"
        val serialized = record.serialize()
        sessionCache[key] = serialized
        addSessionKey(key)
        prefs.edit().putString(key, Base64.encodeToString(serialized, Base64.NO_WRAP)).commit()
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        val key = "session_${address.name}_${address.deviceId}"
        sessionCache.remove(key)
        removeSessionKey(key)
        prefs.edit().remove(key).commit()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        val key = "session_${address.name}_${address.deviceId}"
        return sessionCache.containsKey(key) || prefs.contains(key)
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        val result = mutableListOf<SessionRecord>()
        addresses?.forEach { address ->
            if (containsSession(address)) {
                result.add(loadSession(address))
            }
        }
        return result
    }

    override fun deleteAllSessions(name: String) {
        sessionCache.keys.filter { it.startsWith("session_${name}_") }.forEach { sessionCache.remove(it) }
        val sessionKeys = getSessionKeys()
        val toRemove = sessionKeys.filter { it.startsWith("session_${name}_") }
        if (toRemove.isNotEmpty()) {
            val current = getSessionKeys().toMutableSet()
            current.removeAll(toRemove)
            sessionKeysCache = current
            val editor = prefs.edit()
            editor.putStringSet("session_keys_list", current)
            toRemove.forEach { editor.remove(it) }
            editor.commit()
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()

    // -- PreKeyStore --

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val serialized = preKeyCache[preKeyId] ?: prefs.getString("prekey_$preKeyId", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        requireNotNull(serialized) { "No such prekeyrecord: $preKeyId" }
        return PreKeyRecord(serialized)
    }

    fun loadPreKeys(): List<PreKeyRecord> {
        val ids = getPreKeyIds()
        return ids.mapNotNull { idStr ->
            val preKeyId = idStr.toIntOrNull() ?: return@mapNotNull null
            try {
                loadPreKey(preKeyId)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        val serialized = record.serialize()
        preKeyCache[preKeyId] = serialized
        addPreKeyId(preKeyId)
        prefs.edit().putString("prekey_$preKeyId", Base64.encodeToString(serialized, Base64.NO_WRAP)).commit()
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyCache.containsKey(preKeyId) || prefs.contains("prekey_$preKeyId")
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyCache.remove(preKeyId)
        removePreKeyId(preKeyId)
        prefs.edit().remove("prekey_$preKeyId").commit()
    }

    // -- SignedPreKeyStore --

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val serialized = signedPreKeyCache[signedPreKeyId] ?: prefs.getString("signed_prekey_$signedPreKeyId", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        requireNotNull(serialized) { "No such signedprekeyrecord: $signedPreKeyId" }
        return SignedPreKeyRecord(serialized)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        val ids = getSignedPreKeyIds()
        return ids.mapNotNull { idStr ->
            val signedPreKeyId = idStr.toIntOrNull() ?: return@mapNotNull null
            try {
                loadSignedPreKey(signedPreKeyId)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        val serialized = record.serialize()
        signedPreKeyCache[signedPreKeyId] = serialized
        addSignedPreKeyId(signedPreKeyId)
        prefs.edit().putString("signed_prekey_$signedPreKeyId", Base64.encodeToString(serialized, Base64.NO_WRAP)).commit()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeyCache.containsKey(signedPreKeyId) || prefs.contains("signed_prekey_$signedPreKeyId")
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyCache.remove(signedPreKeyId)
        removeSignedPreKeyId(signedPreKeyId)
        prefs.edit().remove("signed_prekey_$signedPreKeyId").commit()
    }

    // -- KyberPreKeyStore --
    override fun loadKyberPreKey(kyberPreKeyId: Int): org.signal.libsignal.protocol.state.KyberPreKeyRecord {
        throw org.signal.libsignal.protocol.InvalidKeyIdException("Not implemented")
    }
    
    override fun loadKyberPreKeys(): MutableList<org.signal.libsignal.protocol.state.KyberPreKeyRecord> = mutableListOf()
    override fun storeKyberPreKey(kyberPreKeyId: Int, record: org.signal.libsignal.protocol.state.KyberPreKeyRecord) {}
    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = false
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {}

    // -- SenderKeyStore --
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: java.util.UUID, record: org.signal.libsignal.protocol.groups.state.SenderKeyRecord) {
        throw UnsupportedOperationException("Group messaging not supported")
    }
    
    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: java.util.UUID): org.signal.libsignal.protocol.groups.state.SenderKeyRecord {
        throw UnsupportedOperationException("Group messaging not supported")
    }
}
