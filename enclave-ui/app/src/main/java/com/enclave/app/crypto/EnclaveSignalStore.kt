package com.enclave.app.crypto

import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val ioScope = CoroutineScope(Dispatchers.IO)

    // In-memory caches for fast Double Ratchet reads/writes (to prevent UI stutter)
    private val sessionCache = ConcurrentHashMap<String, ByteArray>()
    private val preKeyCache = ConcurrentHashMap<Int, ByteArray>()
    private val signedPreKeyCache = ConcurrentHashMap<Int, ByteArray>()

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
        prefs.edit().putString("identity_${address.name}", Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)).apply()
        return true
    }

    override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
        val serialized = Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)
        val trusted = prefs.getString("identity_${address.name}", null)
        return if (trusted == null) {
            prefs.edit().putString("identity_${address.name}", serialized).apply()
            true
        } else {
            trusted == serialized
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

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val key = "session_${address.name}_${address.deviceId}"
        val serialized = record.serialize()
        sessionCache[key] = serialized
        ioScope.launch {
            prefs.edit().putString(key, Base64.encodeToString(serialized, Base64.NO_WRAP)).apply()
        }
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

    override fun deleteSession(address: SignalProtocolAddress) {
        val key = "session_${address.name}_${address.deviceId}"
        sessionCache.remove(key)
        ioScope.launch { prefs.edit().remove(key).apply() }
    }

    override fun deleteAllSessions(name: String) {
        sessionCache.keys.filter { it.startsWith("session_${name}_") }.forEach { sessionCache.remove(it) }
        val keys = prefs.all.keys.filter { it.startsWith("session_${name}_") }
        if (keys.isNotEmpty()) {
            ioScope.launch {
                val editor = prefs.edit()
                keys.forEach { editor.remove(it) }
                editor.apply()
            }
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
        val keys = prefs.all.keys.filter { it.startsWith("prekey_") }
        return keys.mapNotNull {
            prefs.getString(it, null)?.let { b64 -> PreKeyRecord(Base64.decode(b64, Base64.NO_WRAP)) }
        }
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        val serialized = record.serialize()
        preKeyCache[preKeyId] = serialized
        ioScope.launch {
            prefs.edit().putString("prekey_$preKeyId", Base64.encodeToString(serialized, Base64.NO_WRAP)).apply()
        }
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyCache.containsKey(preKeyId) || prefs.contains("prekey_$preKeyId")
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyCache.remove(preKeyId)
        ioScope.launch { prefs.edit().remove("prekey_$preKeyId").apply() }
    }

    // -- SignedPreKeyStore --

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val serialized = signedPreKeyCache[signedPreKeyId] ?: prefs.getString("signed_prekey_$signedPreKeyId", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        requireNotNull(serialized) { "No such signedprekeyrecord: $signedPreKeyId" }
        return SignedPreKeyRecord(serialized)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        val keys = prefs.all.keys.filter { it.startsWith("signed_prekey_") }
        return keys.mapNotNull {
            prefs.getString(it, null)?.let { b64 -> SignedPreKeyRecord(Base64.decode(b64, Base64.NO_WRAP)) }
        }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        val serialized = record.serialize()
        signedPreKeyCache[signedPreKeyId] = serialized
        ioScope.launch {
            prefs.edit().putString("signed_prekey_$signedPreKeyId", Base64.encodeToString(serialized, Base64.NO_WRAP)).apply()
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeyCache.containsKey(signedPreKeyId) || prefs.contains("signed_prekey_$signedPreKeyId")
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyCache.remove(signedPreKeyId)
        ioScope.launch { prefs.edit().remove("signed_prekey_$signedPreKeyId").apply() }
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
