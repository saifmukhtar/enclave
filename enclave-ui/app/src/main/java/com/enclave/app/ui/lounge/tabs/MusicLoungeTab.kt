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

            val playlistQueue by viewModel.playlistQueue.collectAsState()
            var currentSubTab by remember { mutableStateOf("library") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    "library" to "🎵 Songs Library",
                    "queue" to "📋 Shared Queue"
                ).forEach { (id, label) ->
                    val isSel = currentSubTab == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) Color(0xFFE598A7) else Color(0xFFFCE2E6))
                            .clickable { currentSubTab = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color(0xFF2A1B1D)
                        )
                    }
                }
            }

            if (currentSubTab == "library") {
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
                                    IconButton(
                                        onClick = { viewModel.addToQueue(song.id.orEmpty()) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Queue,
                                            contentDescription = "Add to Queue",
                                            tint = Color(0xFFE598A7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

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
            } else {
                val resolvedQueue = playlistQueue.mapNotNull { queueItem ->
                    val song = loungeSongs.find { it.id == queueItem.song_id }
                    if (song != null) Pair(queueItem, song) else null
                }

                if (resolvedQueue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Queue is empty.\nAdd songs from the Library! 🎵",
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
                        items(resolvedQueue) { (queueItem, song) ->
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
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(queueItem.id.orEmpty()) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Remove from queue",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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

