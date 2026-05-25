@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.lounge.tabs

import com.enclave.app.ui.lounge.*
import com.enclave.app.ui.lounge.components.*
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
fun MusicLoungeTab(
    
    musicSyncController: MusicSyncController?,
    loungeMusicFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    if (musicSyncController == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFE598A7))
        }
        return
    }

    val musicViewModel: com.enclave.app.ui.lounge.LoungeMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeMusicFactory)

    val context = LocalContext.current
    val isPlaying by musicSyncController.isPlaying.collectAsState()
    val currentPosition by musicSyncController.currentPosition.collectAsState()
    val duration by musicSyncController.duration.collectAsState()
    val currentTrackName by musicSyncController.currentTrackName.collectAsState()
    val shuffleEnabled by musicSyncController.shuffleEnabled.collectAsState()
    val sleepTimerRemaining by musicSyncController.sleepTimerRemaining.collectAsState()

    val loungeSongs by musicViewModel.loungeSongs.collectAsState()

    // Sync playlist whenever lounge songs change
    LaunchedEffect(loungeSongs) {
        musicSyncController.setPlaylist(loungeSongs.map { it.url to it.title })
    }

    var showPlaylistView by rememberSaveable { mutableStateOf(false) }

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
                    musicViewModel.uploadAndAddSong(displayName, bytes)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showPlaylistView) {
        MusicQueueList(
            musicViewModel = musicViewModel,
            musicSyncController = musicSyncController,
            onClose = { showPlaylistView = false },
            audioPickerLauncher = audioPickerLauncher
        )
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
            VinylRecordRenderer(isPlaying = isPlaying)

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
            MusicPlayerControls(
                musicSyncController = musicSyncController,
                currentTrackName = currentTrackName,
                currentPosition = currentPosition,
                duration = duration,
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                sleepTimerRemaining = sleepTimerRemaining
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

