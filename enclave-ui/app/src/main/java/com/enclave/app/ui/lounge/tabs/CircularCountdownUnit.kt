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
// 9. Composable Helpers for UI
// ==========================================
@Composable
fun CircularCountdownUnit(
    value: Long,
    label: String,
    progress: Float,
    color: Color = Color(0xFFE598A7)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = String.format("%02d", value),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2A1B1D),
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
    }
}

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmapState by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        if (url.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            bitmapState = bmp.asImageBitmap()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NetworkImage", "Failed to download image from $url", e)
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFE598A7),
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            val bitmap = bitmapState
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            } else {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Broken image",
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun MemoryTimelineTab(chatViewModel: com.enclave.app.ui.chat.ChatViewModel?) {
    if (chatViewModel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Timeline not available.", color = Color.Gray)
        }
        return
    }

    val mediaMessages by chatViewModel.mediaMessages.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Memory Timeline 📅", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2A1B1D), modifier = Modifier.padding(bottom = 16.dp))
        
        if (mediaMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No shared media yet.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mediaMessages.sortedByDescending { it.timestamp }) { msg ->
                    MemoryTimelineItem(msg, chatViewModel)
                }
            }
        }
    }
}

@Composable
fun MemoryTimelineItem(msg: com.enclave.app.ui.chat.ChatMessage, chatViewModel: com.enclave.app.ui.chat.ChatViewModel) {
    var bmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(msg.id) {
        val bytes = chatViewModel.getMediaBytes(msg.id)
        if (bytes != null) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp = bitmap?.asImageBitmap()
        }
        loaded = true
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!loaded) {
                CircularProgressIndicator(color = Color(0xFFE598A7))
            } else if (bmp != null) {
                Image(bitmap = bmp!!, contentDescription = "Media", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Unsupported media or decryption failed", color = Color.Gray)
            }
            
            // Timestamp overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                Text(date, color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun E2EENotesTab(loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory) {
    val viewModel: com.enclave.app.ui.lounge.LoungeMediaViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeMediaFactory)
    val notes by viewModel.encryptedNotesFlow.collectAsState(initial = emptyList())
    var showComposer by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var viewingNote by remember { mutableStateOf<com.enclave.app.data.local.EncryptedNoteEntity?>(null) }
    var decryptedTitle by remember { mutableStateOf("") }
    var decryptedContent by remember { mutableStateOf("") }

    if (showComposer) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showComposer = false; noteTitle = ""; noteContent = "" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2A1B1D))
                }
                Text("Compose Secure Note", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 16.sp)
                IconButton(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                            viewModel.saveEncryptedNote(title = noteTitle, content = noteContent)
                            showComposer = false
                            noteTitle = ""
                            noteContent = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save Note", tint = Color(0xFFE598A7))
                }
            }
            OutlinedTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text("Note content...") },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }
        return
    }

    if (viewingNote != null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = { viewingNote = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2A1B1D))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(decryptedTitle, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF2A1B1D))
            Spacer(modifier = Modifier.height(16.dp))
            Text(decryptedContent, fontSize = 16.sp, color = Color.DarkGray)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("E2EE Shared Notes", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 18.sp)
            IconButton(onClick = { showComposer = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Note", tint = Color(0xFFE598A7))
            }
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No encrypted notes yet. Tap + to create one.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes) { note ->
                    val title = remember(note.titlePayload) { viewModel.decryptNoteField(note.titlePayload) }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            decryptedTitle = viewModel.decryptNoteField(note.titlePayload)
                            decryptedContent = viewModel.decryptNoteField(note.contentPayload)
                            viewingNote = note
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2A1B1D))
                                val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt))
                                Text(date, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.deleteEncryptedNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}


