package dev.saifmukhtar.enclave.ui.bootstrap.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

enum class DiagnosticStatus {
    IDLE, RUNNING, SUCCESS, FAILED
}

@Composable
fun ConnectionDiagnosticsDialog(
    supabaseUrl: String,
    supabaseKey: String,
    signalingUrl: String,
    turnUrl: String,
    ntfyUrl: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var supabaseStatus by remember { mutableStateOf(DiagnosticStatus.IDLE) }
    var signalingStatus by remember { mutableStateOf(DiagnosticStatus.IDLE) }
    var ntfyStatus by remember { mutableStateOf(DiagnosticStatus.IDLE) }
    var turnStatus by remember { mutableStateOf(DiagnosticStatus.IDLE) }

    var supabaseError by remember { mutableStateOf("") }
    var signalingError by remember { mutableStateOf("") }
    var ntfyError by remember { mutableStateOf("") }
    var turnError by remember { mutableStateOf("") }

    val client = remember { OkHttpClient.Builder().connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS).build() }

    LaunchedEffect(Unit) {
        // --- 1. Supabase Check ---
        supabaseStatus = DiagnosticStatus.RUNNING
        delay(600)
        val sbSuccess = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$supabaseUrl/rest/v1/")
                    .addHeader("apikey", supabaseKey)
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200 || res.code == 400 || res.code == 401 || res.code == 404) {
                        true
                    } else {
                        supabaseError = "HTTP ${res.code}"
                        false
                    }
                }
            } catch (e: Exception) {
                supabaseError = e.message ?: "Connection Timeout"
                false
            }
        }
        supabaseStatus = if (sbSuccess) DiagnosticStatus.SUCCESS else DiagnosticStatus.FAILED
        if (!sbSuccess) return@LaunchedEffect

        // --- 2. Signaling Server Check ---
        signalingStatus = DiagnosticStatus.RUNNING
        delay(600)
        val sigSuccess = withContext(Dispatchers.IO) {
            try {
                val httpUrl = signalingUrl.replace("wss://", "https://")
                    .replace("ws://", "http://")
                val req = Request.Builder().url("$httpUrl/healthz").build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        true
                    } else {
                        signalingError = "HTTP ${res.code}"
                        false
                    }
                }
            } catch (e: Exception) {
                signalingError = e.message ?: "Connection Timeout"
                false
            }
        }
        signalingStatus = if (sigSuccess) DiagnosticStatus.SUCCESS else DiagnosticStatus.FAILED
        if (!sigSuccess) return@LaunchedEffect

        // --- 3. Ntfy Push Server Check ---
        if (ntfyUrl.isNotBlank()) {
            ntfyStatus = DiagnosticStatus.RUNNING
            delay(600)
            val ntfySuccess = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(ntfyUrl).build()
                    client.newCall(req).execute().use { res ->
                        if (res.code == 200 || res.code == 404 || res.code == 401) {
                            true
                        } else {
                            ntfyError = "HTTP ${res.code}"
                            false
                        }
                    }
                } catch (e: Exception) {
                    ntfyError = e.message ?: "Connection Timeout"
                    false
                }
            }
            ntfyStatus = if (ntfySuccess) DiagnosticStatus.SUCCESS else DiagnosticStatus.FAILED
            if (!ntfySuccess) return@LaunchedEffect
        } else {
            ntfyStatus = DiagnosticStatus.SUCCESS
        }

        // --- 4. Coturn TURN Check ---
        if (turnUrl.isNotBlank()) {
            turnStatus = DiagnosticStatus.RUNNING
            delay(600)
            val turnSuccess = withContext(Dispatchers.IO) {
                try {
                    val cleaned = turnUrl.replace("turn:", "").replace("turns:", "")
                    val host = cleaned.substringBefore(":")
                    val port = cleaned.substringAfter(":", "3478").toIntOrNull() ?: 3478
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 4000)
                        true
                    }
                } catch (e: Exception) {
                    turnError = e.message ?: "Port 3478 Closed"
                    false
                }
            }
            turnStatus = if (turnSuccess) DiagnosticStatus.SUCCESS else DiagnosticStatus.FAILED
            if (!turnSuccess) return@LaunchedEffect
        } else {
            turnStatus = DiagnosticStatus.SUCCESS
        }

        delay(800)
        onSuccess()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F5)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Backend Node Diagnostics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A0812)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Testing server connections...",
                    fontSize = 12.sp,
                    color = Color(0xFF1A0812).copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(24.dp))

                DiagnosticRow("Supabase Cloud", supabaseStatus, supabaseError)
                DiagnosticRow("WebSocket Signaling", signalingStatus, signalingError)
                if (ntfyUrl.isNotBlank()) {
                    DiagnosticRow("ntfy Push Gateway", ntfyStatus, ntfyError)
                }
                if (turnUrl.isNotBlank()) {
                    DiagnosticRow("Coturn TURN Server", turnStatus, turnError)
                }

                Spacer(Modifier.height(24.dp))

                val hasFailure = supabaseStatus == DiagnosticStatus.FAILED ||
                        signalingStatus == DiagnosticStatus.FAILED ||
                        ntfyStatus == DiagnosticStatus.FAILED ||
                        turnStatus == DiagnosticStatus.FAILED

                if (hasFailure) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC83D60))
                        ) {
                            Text("Edit Config", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onSuccess,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Skip / Connect Anyway", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        color = Color(0xFFE598A7),
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    status: DiagnosticStatus,
    error: String
) {
    val blushAccent = Color(0xFFE598A7)
    val deepRose = Color(0xFFC83D60)

    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (status) {
                        DiagnosticStatus.SUCCESS -> Color(0xFFE8F5E9)
                        DiagnosticStatus.FAILED -> Color(0xFFFFEBEE)
                        DiagnosticStatus.RUNNING -> blushAccent.copy(alpha = 0.15f)
                        else -> Color.Black.copy(alpha = 0.05f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                DiagnosticStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                DiagnosticStatus.FAILED -> Icon(Icons.Default.Error, null, tint = deepRose, modifier = Modifier.size(18.dp))
                DiagnosticStatus.RUNNING -> Icon(Icons.Default.PlayArrow, null, tint = blushAccent, modifier = Modifier.size(18.dp))
                else -> Spacer(Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (status == DiagnosticStatus.RUNNING) Color(0xFF1A0812).copy(alpha = alpha) else Color(0xFF1A0812)
            )
            if (status == DiagnosticStatus.FAILED && error.isNotBlank()) {
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = deepRose.copy(alpha = 0.8f)
                )
            }
        }
    }
}
