package com.enclave.app.notifications

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class EnclaveMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("EnclaveMessagingService", "Received silent wake-up FCM push!")

        // Trigger EnclaveSyncWorker as an Expedited Work Request
        val syncRequest = OneTimeWorkRequestBuilder<EnclaveSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("EnclaveMessagingService", "FCM Device Token: $token")
    }
}
