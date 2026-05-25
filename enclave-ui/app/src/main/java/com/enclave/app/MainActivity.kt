package com.enclave.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.enclave.app.ui.main.EnclaveApp
import com.enclave.app.worker.DailyBackupWorker
import com.enclave.app.worker.PreKeyRotationWorker
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.mutableStateOf

class MainActivity : FragmentActivity() {

    private val autoLaunchKissState = mutableStateOf(false)
    private val blockScreenshots = !BuildConfig.DEBUG

    fun setSecureMode(enabled: Boolean) {
        runOnUiThread {
            if (enabled && blockScreenshots) {
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.getBooleanExtra("AUTO_LAUNCH_KISS", false) == true) {
            autoLaunchKissState.value = true
        }

        if (intent?.data?.scheme == "enclave" && intent?.data?.host == "invite") {
            val token = intent?.data?.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                val prefs = getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("partner_id", token).apply()
                android.widget.Toast.makeText(this, "Paired via Invite Link!", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        setSecureMode(true)

        val rotationRequest = PeriodicWorkRequestBuilder<PreKeyRotationWorker>(
            7, TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "PreKeyRotationWork",
            ExistingPeriodicWorkPolicy.KEEP,
            rotationRequest
        )

        val backupRequest = PeriodicWorkRequestBuilder<DailyBackupWorker>(
            24, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnclaveApp(
                        autoLaunchKissState = autoLaunchKissState.value,
                        onKissCanvasClosed = { autoLaunchKissState.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("AUTO_LAUNCH_KISS", false)) {
            autoLaunchKissState.value = true
        }
    }
}
