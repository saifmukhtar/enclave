package dev.saifmukhtar.enclave.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import dev.saifmukhtar.enclave.data.vault.EncryptedFileManager
import java.io.InputStream
import kotlin.math.min

class EncryptedFileDataSource(
    private val encryptedFileManager: EncryptedFileManager,
    private val fileName: String
) : DataSource {

    private var decryptedStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op or delegate
    }

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        val stream = encryptedFileManager.getSecureInputStream(fileName)
        decryptedStream = stream

        val fileLength = encryptedFileManager.getRawFile(fileName).length()

        // Handle ExoPlayer seek offsets
        if (dataSpec.position > 0) {
            stream.skip(dataSpec.position)
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            // Estimate decrypted payload size (raw file length minus IV/Tag bytes roughly)
            val estPayload = (fileLength - 28).coerceAtLeast(0)
            (estPayload - dataSpec.position).coerceAtLeast(0)
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(bytesRemaining.toInt(), length)
        } else length

        val stream = decryptedStream ?: return C.RESULT_END_OF_INPUT
        val read = stream.read(buffer, offset, bytesToRead)
        if (read == -1) return C.RESULT_END_OF_INPUT

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        decryptedStream?.close()
        decryptedStream = null
    }
}
