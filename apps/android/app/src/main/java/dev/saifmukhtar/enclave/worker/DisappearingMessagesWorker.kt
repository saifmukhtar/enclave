package dev.saifmukhtar.enclave.worker

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase

class DisappearingMessagesWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = EnclaveDatabase.getInstance(applicationContext)
            
            database.messageDao().deleteExpiredMessages(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
            Result.retry()
        }
    }
}
