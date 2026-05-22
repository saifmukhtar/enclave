package com.enclave.app.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.data.local.CallLogDao
import com.enclave.app.data.local.CallLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────
// Enclave Call Log Screen — Signal-style call history
// ─────────────────────────────────────────────

private val BlushBg = Color(0xFFFFF5F6)
private val BlushAccent = Color(0xFFE598A7)
private val Charcoal = Color(0xFF2A1B1D)
private val MissedRed = Color(0xFFE53935)
private val ConnectedGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(callLogDao: CallLogDao) {
    val logs by callLogDao.getRecentLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    // Group logs by date label
    val groupedLogs = remember(logs) {
        logs.groupBy { log ->
            val cal = Calendar.getInstance().apply { timeInMillis = log.startedAt }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            when {
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(log.startedAt))
            }
        }
    }

    Scaffold(
        containerColor = BlushBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Call History",
                        fontWeight = FontWeight.Bold,
                        color = Charcoal,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.9f)),
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = Charcoal)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        if (logs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(BlushAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = BlushAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        "No calls yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Charcoal
                    )
                    Text(
                        "Your call history with your partner\nwill appear here.",
                        fontSize = 13.sp,
                        color = Charcoal.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedLogs.forEach { (dateLabel, dayLogs) ->
                    // Date header
                    item(key = "header_$dateLabel") {
                        Text(
                            text = dateLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Charcoal.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    // Log items
                    items(dayLogs, key = { it.id }) { log ->
                        CallLogItem(log)
                    }
                }
                // Bottom spacer
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Confirm clear dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color.White,
            title = { Text("Clear Call History", fontWeight = FontWeight.Bold, color = Charcoal) },
            text = { Text("All call records will be permanently deleted.", color = Charcoal.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { callLogDao.clearAll() }
                    showClearDialog = false
                }) {
                    Text("Clear", color = MissedRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Charcoal.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
private fun CallLogItem(log: CallLogEntity) {
    val isMissed = log.status == "MISSED"
    val isRejected = log.status == "REJECTED"
    val isOutgoing = log.direction == "OUTGOING"
    val isAudio = log.callType == "AUDIO"

    val statusColor = when {
        isMissed || isRejected -> MissedRed
        else -> ConnectedGreen
    }

    val directionIcon = when {
        isMissed || isRejected -> Icons.AutoMirrored.Filled.CallMissed
        isOutgoing -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.AutoMirrored.Filled.CallReceived
    }

    val callTypeIcon = if (isAudio) Icons.Default.Call else Icons.Default.Videocam

    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.startedAt))
    val durationStr = when {
        log.durationSeconds == 0 -> ""
        log.durationSeconds < 60 -> "${log.durationSeconds}s"
        else -> "${log.durationSeconds / 60}m ${log.durationSeconds % 60}s"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Call type icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                BlushAccent.copy(alpha = 0.18f),
                                BlushAccent.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = callTypeIcon,
                    contentDescription = null,
                    tint = BlushAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Call details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            isMissed -> if (isOutgoing) "No Answer" else "Missed"
                            isRejected -> "Declined"
                            isOutgoing -> "Outgoing ${if (isAudio) "Audio" else "Video"} Call"
                            else -> "Incoming ${if (isAudio) "Audio" else "Video"} Call"
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (isMissed || isRejected) MissedRed else Charcoal
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = Charcoal.copy(alpha = 0.45f)
                    )
                    if (durationStr.isNotEmpty()) {
                        Text(
                            text = " · $durationStr",
                            fontSize = 11.sp,
                            color = Charcoal.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}
