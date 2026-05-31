package dev.saifmukhtar.enclave.media

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dev.saifmukhtar.enclave.crypto.CryptoManager
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

class MemoryMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        var length = size
        if (position + length > data.size) {
            length = (data.size - position).toInt()
        }
        System.arraycopy(data, position.toInt(), buffer!!, offset, length)
        return length
    }

    override fun getSize(): Long {
        return data.size.toLong()
    }

    override fun close() {
        // No resources to release
    }
}

class VoiceMemoController(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempRecordFile: File? = null

    private val recorderLock = Any()

    fun startRecording(): Boolean {
        synchronized(recorderLock) {
            return try {
                tempRecordFile = File(context.cacheDir, "voice_record_temp.m4a")
                if (tempRecordFile?.exists() == true) {
                    tempRecordFile?.delete()
                }
                
                mediaRecorder = MediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(tempRecordFile?.absolutePath)
                    prepare()
                    start()
                }
                true
            } catch (e: Exception) {
                android.util.Log.e("Enclave", "Exception caught", e)
                false
            }
        }
    }

    fun stopRecording(): ByteArray? {
        synchronized(recorderLock) {
            return try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                
                val file = tempRecordFile ?: return null
                if (!file.exists()) return null
                
                val bytes = file.readBytes()
                shredFile(file)
                tempRecordFile = null
                
                bytes
            } catch (e: Exception) {
                android.util.Log.e("Enclave", "Exception caught", e)
                null
            }
        }
    }

    fun cancelRecording() {
        synchronized(recorderLock) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                android.util.Log.e("Enclave", "Exception caught", e)
            }
            mediaRecorder = null
            tempRecordFile?.let { shredFile(it) }
            tempRecordFile = null
        }
    }

    fun playVoiceMemo(audioBytes: ByteArray, onComplete: () -> Unit = {}) {
        stopPlayback()
        try {
            val dataSource = MemoryMediaDataSource(audioBytes)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(dataSource)
                prepare()
                setOnCompletionListener {
                    onComplete()
                    stopPlayback()
                }
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
        }
        mediaPlayer = null
    }

    fun getAmplitude(): Int {
        synchronized(recorderLock) {
            return try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }

    private fun shredFile(file: File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            val raf = RandomAccessFile(file, "rws")
            val random = SecureRandom()
            val buffer = ByteArray(4096)
            var written = 0L
            while (written < length) {
                random.nextBytes(buffer)
                val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                raf.write(buffer, 0, toWrite)
                written += toWrite
            }
            raf.setLength(0)
            raf.close()
            file.delete()
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
            file.delete()
        }
    }
}
