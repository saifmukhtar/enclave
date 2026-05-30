package com.enclave.app.ui.bootstrap

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.enclave.app.MainActivity
import android.content.Intent

class BootstrapActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // We do not enforce FLAG_SECURE on the bootstrap setup screen
        // to allow users to take screenshots or display their pairing QR codes cleanly.
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            com.enclave.app.ui.theme.EnclaveTheme {
                BootstrapScreen(
                    onSetupComplete = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
