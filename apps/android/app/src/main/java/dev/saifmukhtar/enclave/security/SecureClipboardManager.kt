package dev.saifmukhtar.enclave.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SecureClipboardManager {
    private const val TAG = "SecureClipboard"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Copies the provided [text] to the system clipboard and schedules its removal
     * after [timeoutMillis] if the clipboard contents have not changed in the meantime.
     */
    fun copyAndScheduleClear(
        context: Context,
        text: String,
        label: String = "Enclave Secure Copy",
        timeoutMillis: Long = 30000L
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clipData)
            Log.d(TAG, "Copied text to clipboard. Clearing scheduled in ${timeoutMillis / 1000}s.")

            scope.launch {
                delay(timeoutMillis)
                clearIfMatches(clipboard, text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy or schedule clipboard clear", e)
        }
    }

    private fun clearIfMatches(clipboard: ClipboardManager, originalText: String) {
        try {
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString()
                if (currentText == originalText) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        // Fallback for older versions
                        val emptyClip = ClipData.newPlainText("", "")
                        clipboard.setPrimaryClip(emptyClip)
                    }
                    Log.d(TAG, "Securely cleared Enclave payload from clipboard.")
                } else {
                    Log.d(TAG, "Clipboard text changed. Retaining new user clipboard contents.")
                }
            }
        } catch (e: Exception) {
            // Silence background access/security exceptions safely
            Log.w(TAG, "Could not check/clear clipboard (possibly due to background lifecycle limits)", e)
        }
    }
}
