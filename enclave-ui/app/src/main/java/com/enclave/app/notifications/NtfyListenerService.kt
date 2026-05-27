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
        private const val ALERT_CHANNEL_ID = "enclave_alerts"
        private const val NOTIFICATION_ID = 2586
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 300_000L // 5 minutes max
    }

    private lateinit var okHttpClient: OkHttpClient
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var currentBackoffMs = INITIAL_BACKOFF_MS

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

        Log.d(TAG, "Connecting to ntfy WebSocket: $wsUrl (backoff=${currentBackoffMs}ms)")

        val credentials = Credentials.basic(BuildConfig.NTFY_USERNAME, BuildConfig.NTFY_PASSWORD)
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", credentials)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "ntfy WebSocket connected")
                isConnected = true
                currentBackoffMs = INITIAL_BACKOFF_MS // Reset backoff on success
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "ntfy WebSocket received message: $text")
                try {
                    val json = org.json.JSONObject(text)
                    val event = json.optString("event")
                    if (event == "message") {
                        val title = json.optString("title", "Enclave Update")
                        val message = json.optString("message", "You have a new private update.")
                        showNotification(title, message)
                        triggerSync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse ntfy WebSocket message", e)
                    triggerSync()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "ntfy WebSocket closed: $reason")
                isConnected = false
                // Normal closure — retry with backoff
                scheduleReconnect(topic)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val statusCode = response?.code ?: -1
                Log.e(TAG, "ntfy WebSocket failure (HTTP $statusCode)", t)
                isConnected = false

                when (statusCode) {
                    401, 403 -> {
                        // Auth failure — retrying won't help until credentials change.
                        // Stop the reconnect loop to avoid hammering the server.
                        Log.e(TAG, "ntfy auth failure ($statusCode) — stopping reconnect loop. Restart service to retry.")
                        stopSelf()
                    }
                    else -> {
                        // Network/server error — retry with exponential backoff
                        scheduleReconnect(topic)
                    }
                }
            }
        })
    }

    private fun scheduleReconnect(topic: String) {
        val backoff = currentBackoffMs
        Log.d(TAG, "ntfy WebSocket will reconnect in ${backoff}ms")
        // Exponential backoff: double each time, cap at MAX_BACKOFF_MS
        currentBackoffMs = minOf(currentBackoffMs * 2, MAX_BACKOFF_MS)

        CoroutineScope(Dispatchers.IO).launch {
            delay(backoff)
            connectWebSocket(topic)
        }
    }

    private fun triggerSync() {
        Log.d(TAG, "Triggering EnclaveSyncWorker via Ntfy Ping")
        val syncRequest = OneTimeWorkRequestBuilder<EnclaveSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    private fun showNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(0xFFFCE2E6.toInt()) // Blush Soft Pink Accent
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Background service sync channel (low importance)
        val syncChannel = NotificationChannel(
            CHANNEL_ID,
            "Background Sync",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Maintains connection to private push server"
            setShowBadge(false)
        }
        manager.createNotificationChannel(syncChannel)

        // User alert channel (high importance)
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Private Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming private chat messages, lounge activities, and stories"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(alertChannel)
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
