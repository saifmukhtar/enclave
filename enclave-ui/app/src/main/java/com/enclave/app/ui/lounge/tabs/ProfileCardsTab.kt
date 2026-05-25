@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.lounge.tabs

import com.enclave.app.ui.lounge.*
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.data.local.LetterEntity
import com.enclave.app.media.MusicSyncController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.foundation.BorderStroke
import java.io.File

// ==========================================
// 1. ✨ Profile Status Cards Tab View (Scrollable, Countdown Widget, Pulse Heartbeat, SQLite Backups)
// ==========================================
@Composable
fun ProfileCardsTab(
    viewModel: LoungeViewModel,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel?
) {
    val myStatus by viewModel.myStatus.collectAsState()
    val partnerStatus by viewModel.partnerStatus.collectAsState()
    val myProfile by profileViewModel?.myProfile?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerProfile by profileViewModel?.partnerProfile?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editEmoji by remember { mutableStateOf(myStatus.moodEmoji) }
    var editMsg by remember { mutableStateOf(myStatus.statusText) }
    var editListen by remember { mutableStateOf(myStatus.nowListening) }
    var editCity by remember { mutableStateOf("") }

    val myProfileVal by viewModel.myProfile.collectAsState()

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            editCity = myProfileVal?.locationCity.orEmpty()
        }
    }

    var showCountdownDialog by remember { mutableStateOf(false) }
    var countdownLabelInput by remember { mutableStateOf("Our Next Visit ✈️") }
    var countdownDaysInput by remember { mutableStateOf("7") }

    // Heartbeat Sync holds states
    var isHoldingHeartbeat by remember { mutableStateOf(false) }
    
    // Animate glowing heartbeat pulse
    val heartbeatScale by animateFloatAsState(
        targetValue = if (isHoldingHeartbeat) 1.25f else 1.0f,
        animationSpec = if (isHoldingHeartbeat) {
            infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        }
    )

    // WebSocket heartbeat ticker while button is held down
    LaunchedEffect(isHoldingHeartbeat) {
        if (isHoldingHeartbeat) {
            while (true) {
                viewModel.sendHeartbeat()
                delay(800L) // Safe throttled heartbeat interval
            }
        }
    }

    // Ephemeral Countdown timer ticker logic
    val targetTime = if (partnerStatus.countdownTarget > 0) partnerStatus.countdownTarget else myStatus.countdownTarget
    val targetLabel = if (partnerStatus.countdownLabel.isNotEmpty()) partnerStatus.countdownLabel else myStatus.countdownLabel
    
    var days by remember { mutableStateOf(0L) }
    var hours by remember { mutableStateOf(0L) }
    var minutes by remember { mutableStateOf(0L) }
    var seconds by remember { mutableStateOf(0L) }
    var isExpired by remember { mutableStateOf(true) }

    LaunchedEffect(targetTime) {
        while (true) {
            val diff = targetTime - System.currentTimeMillis()
            if (diff <= 0) {
                isExpired = true
                days = 0L
                hours = 0L
                minutes = 0L
                seconds = 0L
            } else {
                isExpired = false
                days = diff / (1000 * 60 * 60 * 24)
                hours = (diff / (1000 * 60 * 60)) % 24
                minutes = (diff / (1000 * 60)) % 60
                seconds = (diff / 1000) % 60
            }
            delay(1000)
        }
    }


    // State variables for Passphrase-Encrypted backups
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var showImportPassphraseDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportPassphraseConfirm by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var selectedBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // SAF Create Document launcher for exporting backup
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSecureBackup(
                uri = uri,
                passphrase = exportPassphrase,
                onSuccess = {
                    Toast.makeText(context, "✅ Security Backup Exported Successfully!", Toast.LENGTH_LONG).show()
                },
                onFailure = { err ->
                    Toast.makeText(context, "❌ Export Failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // SAF Open Document launcher for restoring backup
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedBackupUri = uri
            showImportPassphraseDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val myName = myProfile?.displayName?.ifBlank { myProfile?.username }?.ifBlank { "You" } ?: "You"
        val myUsername = myProfile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

        val partnerName = partnerProfile?.displayName?.ifBlank { partnerProfile?.username }?.ifBlank { "Partner" } ?: "Partner"
        val partnerUsername = partnerProfile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "You" Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(260.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = myName.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE598A7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (myUsername.isNotBlank()) {
                            Text(
                                text = myUsername,
                                fontSize = 9.sp,
                                color = Color.Gray.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier.size(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFF5F6), Color(0xFFE598A7), Color(0xFFFFF5F6))
                                    )
                                )
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White, CircleShape)
                                    .padding(2.dp)
                            ) {
                                com.enclave.app.ui.profile.E2eeAvatar(
                                    avatarBase64 = myProfile?.avatarLocalPath,
                                    isMe = true,
                                    profileViewModel = profileViewModel,
                                    initials = myName.take(2),
                                    size = 64.dp,
                                    displayName = myProfile?.displayName?.ifBlank { myProfile?.username } ?: "You",
                                    username = myProfile?.username,
                                    bio = myProfile?.bio,
                                    statusText = myStatus.statusText,
                                    enablePreview = true
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFFFF5F6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(myStatus.moodEmoji.ifBlank { "❤️" }, fontSize = 12.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(myStatus.statusText, fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        val myWeatherStr = if (myStatus.weatherTemp != -999.0) {
                            " • ${myStatus.weatherCondition} ${myStatus.weatherTemp.toInt()}°C"
                        } else ""
                        Text("🔋 Battery: ${myStatus.batteryPct}%$myWeatherStr", fontSize = 10.sp, color = Color.Gray)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF5F6))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Listen", tint = Color(0xFFE598A7), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(myStatus.nowListening, fontSize = 9.sp, color = Color(0xFF2A1B1D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // "Partner" Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(260.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = partnerName.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2A1B1D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (partnerUsername.isNotBlank()) {
                            Text(
                                text = partnerUsername,
                                fontSize = 9.sp,
                                color = Color.Gray.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.size(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFF5F6), Color(0xFFE598A7), Color(0xFFFFF5F6))
                                    )
                                )
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White, CircleShape)
                                    .padding(2.dp)
                            ) {
                                com.enclave.app.ui.profile.E2eeAvatar(
                                    avatarBase64 = partnerProfile?.avatarUrl,
                                    isMe = false,
                                    profileViewModel = profileViewModel,
                                    initials = partnerName.take(2),
                                    size = 64.dp,
                                    displayName = partnerProfile?.displayName?.ifBlank { partnerProfile?.username } ?: "Partner",
                                    username = partnerProfile?.username,
                                    bio = partnerProfile?.bio,
                                    statusText = partnerStatus.statusText,
                                    enablePreview = true
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFFFF5F6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(partnerStatus.moodEmoji.ifBlank { "❤️" }, fontSize = 12.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(partnerStatus.statusText, fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        val partnerWeatherStr = if (partnerStatus.weatherTemp != -999.0) {
                            " • ${partnerStatus.weatherCondition} ${partnerStatus.weatherTemp.toInt()}°C"
                        } else ""
                        Text("🔋 Battery: ${partnerStatus.batteryPct}% • 🕒 ${partnerStatus.localTimeStr}$partnerWeatherStr", fontSize = 10.sp, color = Color.Gray)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFCE2E6))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Listen", tint = Color(0xFF2A1B1D), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(partnerStatus.nowListening, fontSize = 9.sp, color = Color(0xFF2A1B1D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Set Status Button
        Button(
            onClick = { showEditDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Status", tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update My Presence Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Shared visit countdown clock widget
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shared Visit Countdown", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                        Text(
                            text = if (targetLabel.isNotEmpty()) targetLabel else "Set countdown to keep track of next meeting!",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { showCountdownDialog = true }) {
                        Icon(Icons.Default.Timer, contentDescription = "Edit timer", tint = Color(0xFFE598A7))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (isExpired) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFF5F6))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (targetLabel.isNotEmpty()) "Countdown expired! 💕" else "No countdown active",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE598A7)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularCountdownUnit(
                            value = days,
                            label = "Days",
                            progress = if (days > 30) 1.0f else days / 30.0f
                        )
                        CircularCountdownUnit(
                            value = hours,
                            label = "Hours",
                            progress = hours / 24.0f
                        )
                        CircularCountdownUnit(
                            value = minutes,
                            label = "Mins",
                            progress = minutes / 60.0f
                        )
                        CircularCountdownUnit(
                            value = seconds,
                            label = "Secs",
                            progress = seconds / 60.0f
                        )
                    }
                }

            }
        }

        // Pulse Heartbeat Widget Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tactile Heartbeat Synchronizer", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                Text("Hold the heart to transmit your real-time vital sign pulse to your partner.", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(heartbeatScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFF5F6), Color(0xFFFCE2E6))
                            )
                        )
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    isHoldingHeartbeat = true
                                    waitForUpOrCancellation()
                                    isHoldingHeartbeat = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Pulse heart",
                        tint = if (isHoldingHeartbeat) Color.Red else Color(0xFFE598A7),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
        }

        // SQLite Secure Backup Management Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Enclave Database Encrypted Backup", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                Text("Safeguard or migrate your ratchet databases via secure AES-256 GCM passphrase-encrypted backup files.", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            showExportPassphraseDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            importBackupLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Backup", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Update My Lounge Presence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editEmoji,
                        onValueChange = { editEmoji = it },
                        label = { Text("Mood Emoji (❤️, 😊, 🍿, 💤)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMsg,
                        onValueChange = { editMsg = it },
                        label = { Text("Presence Status") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editListen,
                        onValueChange = { editListen = it },
                        label = { Text("🎵 Now Listening") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCity,
                        onValueChange = { editCity = it },
                        label = { Text("🌍 City for Weather Sync") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateMyStatus(editEmoji, editMsg, editListen)
                        viewModel.updateProfileLocation(editCity)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                ) {
                    Text("Save Status", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showCountdownDialog) {
        AlertDialog(
            onDismissRequest = { showCountdownDialog = false },
            title = { Text("Set Shared Meeting Date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countdownLabelInput,
                        onValueChange = { countdownLabelInput = it },
                        label = { Text("Countdown Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = countdownDaysInput,
                        onValueChange = { countdownDaysInput = it },
                        label = { Text("Days from now") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedDays = countdownDaysInput.toLongOrNull() ?: 7
                        val targetMs = System.currentTimeMillis() + (parsedDays * 86400 * 1000)
                        viewModel.setCountdown(countdownLabelInput, targetMs)
                        showCountdownDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                ) {
                    Text("Synchronize Countdown", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCountdownDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showExportPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { 
                showExportPassphraseDialog = false
                exportPassphrase = ""
                exportPassphraseConfirm = ""
            },
            title = { Text("Set Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a secure passphrase to protect your chats and cryptographic keys. \n\nWarning: If you forget this passphrase, your backup cannot be restored.", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportPassphraseConfirm,
                        onValueChange = { exportPassphraseConfirm = it },
                        label = { Text("Confirm Passphrase") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportPassphrase.length >= 6 && exportPassphrase == exportPassphraseConfirm) {
                            showExportPassphraseDialog = false
                            exportBackupLauncher.launch("enclave_backup_${System.currentTimeMillis()}.enclave_backup")
                        } else {
                            Toast.makeText(context, "❌ Passphrases must match and be at least 6 characters long", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                    enabled = exportPassphrase.length >= 6 && exportPassphrase == exportPassphraseConfirm
                ) {
                    Text("Continue", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExportPassphraseDialog = false
                    exportPassphrase = ""
                    exportPassphraseConfirm = ""
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showImportPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportPassphraseDialog = false
                importPassphrase = ""
            },
            title = { Text("Enter Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase that was set when this backup file was created.", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPassphrase.isNotEmpty() && selectedBackupUri != null) {
                            showImportPassphraseDialog = false
                            viewModel.importSecureBackup(
                                uri = selectedBackupUri!!,
                                passphrase = importPassphrase,
                                onSuccess = {
                                    Toast.makeText(context, "✅ Security Backup Restored Successfully! Restarting app...", Toast.LENGTH_LONG).show()
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "❌ Restoration Failed: ${err.message}. Please check your passphrase.", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                    enabled = importPassphrase.isNotEmpty()
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportPassphraseDialog = false
                    importPassphrase = ""
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

