package com.enclave.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.enclave.app.BuildConfig
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NtfyListenerService : Service() {

    companion object {
        private const val TAG = "NtfyListenerService"
        private const val CHANNEL_ID = "ntfy_background_sync"
        private const val NOTIFICATION_ID = 2586
    }

    private lateinit var okHttpClient: OkHttpClient
    private var webSocket: WebSocket? = null
    private var isConnected = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Important for persistent WebSockets
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val topic = prefs.getString("ntfy_topic", null)

        if (topic != null) {
            connectWebSocket(topic)
        } else {
            Log.w(TAG, "No ntfy topic found, cannot start WebSocket")
            stopSelf()
        }

        return START_STICKY
    }

    private fun connectWebSocket(topic: String) {
        if (isConnected) return

        val serverUrl = BuildConfig.NTFY_SERVER_URL
        // Convert http/https to ws/wss
        val wsUrl = serverUrl.replaceFirst("http", "ws") + "/$topic/ws"

        Log.d(TAG, "Connecting to ntfy WebSocket: $wsUrl")

        val credentials = Credentials.basic(BuildConfig.NTFY_USERNAME, BuildConfig.NTFY_PASSWORD)
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", credentials)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "ntfy WebSocket connected")
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "ntfy WebSocket received message: $text")
                triggerSync()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "ntfy WebSocket closed: $reason")
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ntfy WebSocket failure", t)
                isConnected = false
                // Reconnect logic
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000) // Simple backoff
                    connectWebSocket(topic)
                }
            }
        })
    }

    private fun triggerSync() {
        Log.d(TAG, "Triggering EnclaveSyncWorker via Ntfy Ping")
        val syncRequest = OneTimeWorkRequestBuilder<EnclaveSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service destroyed")
        isConnected = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Sync",
            NotificationManager.IMPORTANCE_MIN // Minimizes annoyance, user can still disable channel
        ).apply {
            description = "Maintains connection to private push server"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enclave Sync")
            .setContentText("Maintaining private push connection")
            // A tiny transparent or generic icon can be used if we had one.
            // Using a system icon for the Foreground service
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
