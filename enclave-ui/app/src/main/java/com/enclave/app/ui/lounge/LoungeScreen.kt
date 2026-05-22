@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.lounge

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
import java.io.File

@Composable
fun LoungeScreen(
    viewModel: LoungeViewModel,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel? = null,
    @Suppress("UNUSED_PARAMETER") musicSyncController: MusicSyncController?
) {
    var activeTab by remember { mutableStateOf("profiles") }

    BackHandler(enabled = activeTab != "profiles") {
        activeTab = "profiles"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F6)) // Minimalist Blush palette canvas
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Glowing Header (Blend with theme, no hard boundary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💋 Enclave Shared Lounge",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A1B1D),
                    fontFamily = FontFamily.Serif
                )
            }

            // Minimalist Category Chips Category Row (Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple("profiles", "✨ Profiles", Icons.Default.Person),
                    Triple("letters", "💌 Letters", Icons.Default.Mail),
                    Triple("dice", "🎲 Games", Icons.Default.Casino),
                    Triple("canvas", "🖌️ Live Draw", Icons.Default.Edit),
                    Triple("secret", "🫣 Reveal", Icons.Default.VisibilityOff)
                )

                tabs.forEach { (id, label, icon) ->
                    val isSelected = activeTab == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFFE598A7) else Color(0xFFFCE2E6))
                            .clickable { activeTab = id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Color(0xFF2A1B1D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color(0xFF2A1B1D)
                            )
                        }
                    }
                }
            }

            // Primary Screen Content Viewports
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    "profiles" -> ProfileCardsTab(viewModel, profileViewModel)
                    "letters" -> DailyLettersTab(viewModel)
                    "dice" -> DiceAndIntimacyTab(viewModel)
                    "canvas" -> LiveSharedCanvasTab(viewModel)
                    else -> ScratchToRevealTab(viewModel)
                }
            }
        }
    }
}


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
    var countdownText by remember { mutableStateOf("No countdown active") }

    LaunchedEffect(targetTime) {
        while (true) {
            val diff = targetTime - System.currentTimeMillis()
            if (diff <= 0) {
                countdownText = "Countdown expired! 💕"
            } else {
                val days = diff / (1000 * 60 * 60 * 24)
                val hours = (diff / (1000 * 60 * 60)) % 24
                val minutes = (diff / (1000 * 60)) % 60
                val seconds = (diff / 1000) % 60
                countdownText = "$days Days, $hours Hours, $minutes Mins, $seconds Secs"
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
                        Text("🔋 Battery: ${myStatus.batteryPct}%", fontSize = 10.sp, color = Color.Gray)
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
                        Text("🔋 Battery: ${partnerStatus.batteryPct}% • 🕒 ${partnerStatus.localTimeStr}", fontSize = 10.sp, color = Color.Gray)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF5F6))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = countdownText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE598A7),
                        fontFamily = FontFamily.Monospace
                    )
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateMyStatus(editEmoji, editMsg, editListen)
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
                        val days = countdownDaysInput.toLongOrNull() ?: 7
                        val targetMs = System.currentTimeMillis() + (days * 86400 * 1000)
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

// ==========================================
// 2. 💌 Daily Decrypted In-Memory Letters Tab
// ==========================================
@Composable
fun DailyLettersTab(viewModel: LoungeViewModel) {
    val letters by viewModel.decryptedLettersFlow.collectAsState(initial = emptyList())
    var newLetterText by remember { mutableStateOf("") }
    

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Write New Daily Capsule Letter", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newLetterText,
                    onValueChange = { newLetterText = it },
                    placeholder = { Text("Type something intimate, sweet or hidden...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE598A7),
                        unfocusedBorderColor = Color(0xFFFCE2E6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newLetterText.isNotBlank()) {
                            viewModel.sendLetter(newLetterText)
                            newLetterText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Secure Capsule and Send", color = Color.White)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(letters) { letter ->
                var isDecrypted by remember { mutableStateOf(false) }
                var letterText by remember { mutableStateOf("🔒 Securely Encrypted Daily Capsule") }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (letter.senderId == viewModel.myId) "You Sent" else "Received Partner",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = letterText,
                                fontSize = 13.sp,
                                color = Color(0xFF2A1B1D)
                            )
                        }

                        if (!isDecrypted) {
                            IconButton(
                                onClick = {
                                    letterText = viewModel.decryptLetter(letter.ciphertext)
                                    isDecrypted = true
                                    viewModel.markLetterAsRead(letter.id)
                                }
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Decrypt", tint = Color(0xFFE598A7))
                            }
                        } else {
                            Icon(Icons.Default.LockOpen, contentDescription = "Decrypted", tint = Color(0xFF4CAF50))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. 🎲 3D Tumbling Dice & Intimacy Prompts Tab
// ==========================================
@Composable
fun DiceAndIntimacyTab(viewModel: LoungeViewModel) {
    val isRolling by viewModel.isDiceRolling.collectAsState()
    val diceValue by viewModel.diceValue.collectAsState()
    val currentPrompt by viewModel.currentPrompt.collectAsState()
    val isTruthSelected by viewModel.isTruthSelected.collectAsState()

    val haptic = LocalView.current

    // Trigger local clock-tick haptics coordinated with tumbling frames
    LaunchedEffect(Unit) {
        viewModel.diceTickerEvent.collect {
            haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // 3D rotation animation states
    val rotationX by animateFloatAsState(
        targetValue = if (isRolling) 720f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing)
    )
    val rotationY by animateFloatAsState(
        targetValue = if (isRolling) 720f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tumbling dice display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer {
                            this.rotationX = rotationX
                            this.rotationY = rotationY
                            this.cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFF5F6))
                        .clickable { viewModel.rollDice() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = diceValue.toString(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE598A7)
                    )
                }
            }
        }

        // Intimacy Truth or Dare SWIPER prompts
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isTruthSelected) "Intimacy Truth Capsule" else "Intimacy Dare Challenge",
                    fontWeight = FontWeight.Bold,
                    color = if (isTruthSelected) Color(0xFFE598A7) else Color(0xFF2A1B1D),
                    fontSize = 14.sp
                )

                Text(
                    text = currentPrompt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2A1B1D),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.pickTruthOrDare(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF5F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cozy Truth", color = Color(0xFFE598A7), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.pickTruthOrDare(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Intimate Dare", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. 🖌️ Live Shared Canvas Tab
// ==========================================
@Composable
fun LiveSharedCanvasTab(viewModel: LoungeViewModel) {
    val localStrokes = viewModel.localStrokes
    val partnerStrokes = viewModel.partnerStrokes
    val activeLocalStroke by viewModel.currentLocalStroke.collectAsState()
    val activePartnerStroke by viewModel.currentPartnerStroke.collectAsState()

    var drawColorHex by remember { mutableStateOf("#E598A7") } // Pink default local
    var brushWidth by remember { mutableStateOf(8f) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#E598A7", "#FF5722", "#4CAF50", "#2196F3", "#9C27B0", "#FFEB3B", "#000000").forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (drawColorHex == hex) 2.dp else 0.dp,
                                color = if (drawColorHex == hex) Color.DarkGray else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { drawColorHex = hex }
                    )
                }
            }

            Button(
                onClick = { viewModel.clearCanvas() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Clear Canvas", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { drawColorHex = "#FFFFFF" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (drawColorHex == "#FFFFFF") Color(0xFFE598A7) else Color(0xFF2A1B1D)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Eraser 🧼", color = Color.White, fontSize = 11.sp)
            }

            Text("Size:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            listOf(
                "Thin" to 4f,
                "Medium" to 8f,
                "Thick" to 16f,
                "Jumbo" to 32f
            ).forEach { (label, size) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (brushWidth == size) Color(0xFFFCE2E6) else Color.Transparent)
                        .clickable { brushWidth = size }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 10.sp, color = if (brushWidth == size) Color(0xFFE598A7) else Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            viewModel.startLocalStroke(offset.x / size.width.toFloat(), offset.y / size.height.toFloat(), drawColorHex, brushWidth)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            viewModel.addLocalStrokePoint(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                        },
                        onDragEnd = {
                            viewModel.finalizeLocalStroke()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                fun drawStroke(points: List<LoungePoint>, colorHex: String, width: Float) {
                    if (points.isEmpty()) return
                    if (points.size == 1) {
                        drawCircle(
                            color = Color(android.graphics.Color.parseColor(colorHex)),
                            radius = width / 2,
                            center = androidx.compose.ui.geometry.Offset(points[0].x * w, points[0].y * h)
                        )
                        return
                    }
                    val path = Path()
                    path.moveTo(points[0].x * w, points[0].y * h)
                    for (i in 1 until points.size - 1) {
                        val midX = (points[i].x + points[i + 1].x) / 2 * w
                        val midY = (points[i].y + points[i + 1].y) / 2 * h
                        path.quadraticBezierTo(points[i].x * w, points[i].y * h, midX, midY)

                    }
                    // Connect to the final point
                    path.lineTo(points.last().x * w, points.last().y * h)
                    drawPath(
                        path = path,
                        color = Color(android.graphics.Color.parseColor(colorHex)),
                        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                localStrokes.forEach { drawStroke(it.points, it.colorHex, it.brushWidth) }
                activeLocalStroke?.let { drawStroke(it.points, it.colorHex, it.brushWidth) }
                partnerStrokes.forEach { drawStroke(it.points, it.colorHex, it.brushWidth) }
                activePartnerStroke?.let { drawStroke(it.points, it.colorHex, it.brushWidth) }

            }
        }
    }
}

// ==========================================
// 5. 🫣 Touch-Activated Scratch-to-Reveal / View-Once Tab
// ==========================================
@Composable
fun ScratchToRevealTab(viewModel: LoungeViewModel) {
    val scratchImageBytes by viewModel.scratchImageBytes.collectAsState()
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.setScratchImage(bytes)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load photo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (scratchImageBytes == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF5F6)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Upload Secret Photo",
                tint = Color(0xFFE598A7),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Share a Secret Photo",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1B1D)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your partner will scratch to reveal it. Self-destructs in 5 seconds of active touching.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Select from Gallery", color = Color.White)
            }
        }
    } else {
        val bitmap = remember(scratchImageBytes) {
            try {
                val bytes = scratchImageBytes
                if (bytes != null) {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }

        var remainingTimeMs by remember { mutableStateOf(5000L) }
        var isSelfDestructed by remember { mutableStateOf(false) }
        val pathsScratched = remember { mutableStateListOf<Offset>() }
        var isTouching by remember { mutableStateOf(false) }

        LaunchedEffect(scratchImageBytes) {
            remainingTimeMs = 5000L
            isSelfDestructed = false
            pathsScratched.clear()
            isTouching = false
        }

        LaunchedEffect(isTouching, isSelfDestructed) {
            if (isTouching && !isSelfDestructed) {
                while (remainingTimeMs > 0 && isTouching) {
                    delay(50L)
                    remainingTimeMs -= 50
                }
                if (remainingTimeMs <= 0) {
                    isSelfDestructed = true
                    viewModel.setScratchImage(null)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isSelfDestructed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Shredded",
                        tint = Color(0xFFE598A7),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Love Capsule Shredded!", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Self-destructed successfully after 5s view.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.setScratchImage(null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                    ) {
                        Text("Share Another Secret", color = Color.White)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Secret Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(16.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFFCE2E6), Color(0xFFE598A7)),
                                        center = center,
                                        radius = size.minDimension / 1.5f
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "💋 Intimacy Card",
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isTouching = true
                                    pathsScratched.add(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    pathsScratched.add(change.position)
                                },
                                onDragEnd = {
                                    isTouching = false
                                },
                                onDragCancel = {
                                    isTouching = false
                                }
                            )
                        }
                ) {
                    drawRect(color = Color(0xFFE598A7))

                    pathsScratched.forEach { offset ->
                        drawCircle(
                            color = Color.Transparent,
                            radius = 45.dp.toPx(),
                            center = offset,
                            blendMode = BlendMode.Clear
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⌛ Remaining: ${(remainingTimeMs / 1000f).coerceAtLeast(0f)}s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setScratchImage(null) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. 🎵 Music Lounge Vinyl Tab
// ==========================================
@Composable
fun MusicLoungeTab(viewModel: LoungeViewModel, musicSyncController: MusicSyncController?) {
    if (musicSyncController == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFE598A7))
        }
        return
    }

    val context = LocalContext.current
    val isPlaying by musicSyncController.isPlaying.collectAsState()
    val currentPosition by musicSyncController.currentPosition.collectAsState()
    val duration by musicSyncController.duration.collectAsState()
    val currentTrackName by musicSyncController.currentTrackName.collectAsState()
    val currentTrackUrl by musicSyncController.currentTrackUrl.collectAsState()
    val shuffleEnabled by musicSyncController.shuffleEnabled.collectAsState()
    val sleepTimerRemaining by musicSyncController.sleepTimerRemaining.collectAsState()

    val loungeSongs by viewModel.loungeSongs.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val myId = viewModel.myId

    // Sync playlist whenever lounge songs change
    LaunchedEffect(loungeSongs) {
        musicSyncController.setPlaylist(loungeSongs.map { it.url to it.title })
    }

    var sleepTimerDropdownExpanded by remember { mutableStateOf(false) }
    var showPlaylistView by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Sub-level BackHandler to dismiss the playlist view
    BackHandler(enabled = showPlaylistView) {
        showPlaylistView = false
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                var displayName = "Uploaded Song"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
                displayName = displayName.substringBeforeLast(".")
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.uploadAndAddSong(displayName, bytes)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Infinite rotation transition for the vinyl record
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_infinite")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )
    val vinylAngle = if (isPlaying) rotationAngle else 0f

    // Pivoting tonearm needle angle animation
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 25f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "tonearm_angle"
    )

    if (showPlaylistView) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF5F6)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant Playlist Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { showPlaylistView = false }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF2A1B1D)
                    )
                }

                // Sleek Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs...", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE598A7),
                        unfocusedBorderColor = Color(0xFFFCE2E6),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                // Inline Upload Button next to search bar
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE598A7)
                    )
                } else {
                    IconButton(
                        onClick = {
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            audioPickerLauncher.launch("audio/*")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload MP3",
                            tint = Color(0xFFE598A7)
                        )
                    }
                }
            }

            Text(
                text = "Lounge Playlist (${loungeSongs.size} tracks)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1B1D).copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val filteredSongs = loungeSongs.filter { it.title.contains(searchQuery, ignoreCase = true) }

            if (filteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty())
                            "No songs uploaded yet.\nTap the upload icon to listen together! 🎵"
                            else "No matching songs found.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSongs) { song ->
                        val isSelected = currentTrackUrl == song.url
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFFFFF5F6) else Color.White)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFE598A7) else Color(0xFFFCE2E6),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    musicSyncController.playTrack(song.url, song.title)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFE598A7) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = song.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color(0xFF2A1B1D),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (song.uploaded_by == myId) {
                                    IconButton(
                                        onClick = { viewModel.deleteSong(song) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Gray.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = Color(0xFFE598A7),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Elegant top toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Co-Listening Player",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A1B1D),
                    fontFamily = FontFamily.Serif
                )

                IconButton(onClick = { showPlaylistView = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Open Playlist",
                        tint = Color(0xFFE598A7),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 1. Vinyl Record & Tonearm Canvas Container
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // A. Rotating Vinyl Body
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer(rotationZ = vinylAngle)
                ) {
                    // Vinyl outer disc
                    drawCircle(color = Color(0xFF1C1A1A), radius = size.minDimension / 2)
                    
                    // Groove lines (concentric circles)
                    val totalGrooves = 8
                    val radiusMax = size.minDimension / 2
                    for (i in 1..totalGrooves) {
                        val r = radiusMax * (0.4f + 0.5f * (i.toFloat() / totalGrooves))
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = r,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Vinyl label center (Blush colored)
                    drawCircle(color = Color(0xFFE598A7), radius = radiusMax * 0.35f)
                    
                    // Stylized label design (inner circle)
                    drawCircle(color = Color(0xFF2A1B1D), radius = radiusMax * 0.12f)
                    
                    // Center hole
                    drawCircle(color = Color(0xFFFFF5F6), radius = radiusMax * 0.04f)
                }

                // B. Pivoting Tonearm overlay
                Canvas(
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            rotationZ = tonearmAngle
                            // Rotate around top-right pivot anchor point
                            transformOrigin = TransformOrigin(0.85f, 0.15f)
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val pivotX = w * 0.85f
                    val pivotY = h * 0.15f

                    // Arm base circle
                    drawCircle(
                        color = Color(0xFF8E8E93),
                        radius = 12.dp.toPx(),
                        center = Offset(pivotX, pivotY)
                    )
                    drawCircle(
                        color = Color(0xFF3A3A3C),
                        radius = 6.dp.toPx(),
                        center = Offset(pivotX, pivotY)
                    )

                    // The tonearm rod line (straight arm ending near center)
                    val endX = w * 0.42f
                    val endY = h * 0.52f
                    drawLine(
                        color = Color(0xFFC7C7CC),
                        start = Offset(pivotX, pivotY),
                        end = Offset(endX, endY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Headshell (cartridge housing at the stylus tip)
                    drawRect(
                        color = Color(0xFF3A3A3C),
                        topLeft = Offset(endX - 8.dp.toPx(), endY - 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 12.dp.toPx())
                    )
                }
            }

            // Drifting synchronization indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isPlaying) Color(0xFFE598A7) else Color.Gray, CircleShape)
                )
                Text(
                    text = if (isPlaying) "Listening Together" else "Paused Together",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 2. Track Details Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (currentTrackName.isNotEmpty()) currentTrackName else "Select a Track",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2A1B1D),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Playback progress slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { pct ->
                                val targetMs = (pct * duration).toLong()
                                musicSyncController.seekTo(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE598A7),
                                activeTrackColor = Color(0xFFE598A7),
                                inactiveTrackColor = Color(0xFFFCE2E6)
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(currentPosition), fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text(formatDuration(duration), fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Full controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(
                            onClick = { musicSyncController.toggleShuffle() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleEnabled) Color(0xFFE598A7) else Color.LightGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Previous Button
                        IconButton(
                            onClick = { musicSyncController.playPrevious() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color(0xFFE598A7),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause Button
                        IconButton(
                            onClick = { musicSyncController.togglePlayPause() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFFFFF5F6), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFFE598A7),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Next Button
                        IconButton(
                            onClick = { musicSyncController.playNext() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color(0xFFE598A7),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Sleep Timer Trigger
                        Box {
                            IconButton(
                                onClick = { sleepTimerDropdownExpanded = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerRemaining > 0) Color(0xFFE598A7) else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = sleepTimerDropdownExpanded,
                                onDismissRequest = { sleepTimerDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(0)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("15 min") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(15)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("30 min") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(30)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("60 min") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(60)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("120 min") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(120)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("180 min") },
                                    onClick = {
                                        musicSyncController.setSleepTimer(180)
                                        sleepTimerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Sleep Timer countdown chip
                    if (sleepTimerRemaining > 0) {
                        val timerMinutes = sleepTimerRemaining / 60
                        val timerSeconds = sleepTimerRemaining % 60
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = String.format("Sleep in %02d:%02d 😴", timerMinutes, timerSeconds),
                                    color = Color(0xFFE598A7)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFF5F6)
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

