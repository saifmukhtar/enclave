package com.enclave.app.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.enclave.app.crypto.CryptoManager
import org.signal.libsignal.protocol.SignalProtocolAddress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.min

/**
 * Intercepts E2EE streams from Supabase and decrypts them on the fly for ExoPlayer.
 */
class EncryptedDataSource(
    private val upstream: DataSource,
    private val cryptoManager: CryptoManager,
    private val partnerAddress: SignalProtocolAddress
) : DataSource {

    private var decryptedStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // 1. Open the upstream connection (fetching the encrypted blob from Supabase)
        upstream.open(dataSpec)

        // 2. Buffer the entire ciphertext into memory (acceptable for small audio/voice notes,
        // for massive files this would need a chunked Cipher block stream like AES-CTR).
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (upstream.read(buffer, 0, buffer.size).also { bytesRead = it } != C.RESULT_END_OF_INPUT) {
            outputStream.write(buffer, 0, bytesRead)
        }
        val ciphertext = outputStream.toByteArray()

        // 3. Decrypt on the fly using libsignal Double Ratchet
        val plaintext = cryptoManager.decryptMessage(partnerAddress, ciphertext).getOrThrow()

        // 4. Expose the plaintext to ExoPlayer
        decryptedStream = ByteArrayInputStream(plaintext)
        
        // Handle ExoPlayer seek offsets
        if (dataSpec.position > 0) {
            decryptedStream?.skip(dataSpec.position)
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            (plaintext.size - dataSpec.position).coerceAtLeast(0)
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(bytesRemaining.toInt(), length)
        } else length

        val read = decryptedStream?.read(buffer, offset, bytesToRead) ?: -1
        if (read == -1) return C.RESULT_END_OF_INPUT
        
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        decryptedStream?.close()
        decryptedStream = null
        upstream.close()
    }
}
