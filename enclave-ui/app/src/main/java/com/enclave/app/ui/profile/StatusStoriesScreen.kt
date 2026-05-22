@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.StatusStoryEntity
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.LenientJson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

// ─── ViewModel ────────────────────────────────────────────────────────────────

@Serializable
data class StorySharePayload(
    val storyId: String,
    val contentType: String,
    val encryptedContent: String,
    val backgroundColor: String,
    val expiresAt: Long,
    val createdAt: Long
)

class StoryViewModel(
    application: Application,
    private val database: EnclaveDatabase,
    private val cryptoManager: CryptoManager,
    private val signalingClient: SignalingClient,
    val myId: String,
    private val partnerId: String
) : AndroidViewModel(application) {

    private val storyDao = database.statusStoryDao()

    val myStories: StateFlow<List<StatusStoryEntity>> = storyDao
        .getStoriesByAuthor(myId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val partnerStories: StateFlow<List<StatusStoryEntity>> = storyDao
        .getStoriesByAuthor(partnerId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unviewedCount: StateFlow<Int> = storyDao
        .getUnviewedPartnerStoryCount(partnerId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { storyDao.deleteExpiredStories(System.currentTimeMillis()) }
        observeIncoming()
    }

    private fun observeIncoming() {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { raw ->
                try {
                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(raw)
                    if (msg.senderId != partnerId) return@collect
                    when (msg.type) {
                        "STORY_SHARE" -> {
                            msg.payload?.let { payload ->
                                try {
                                    val story = LenientJson.decodeFromString<StorySharePayload>(payload)
                                    val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                                    val encryptedBytes = android.util.Base64.decode(story.encryptedContent, android.util.Base64.NO_WRAP)
                                    val decryptedResult = cryptoManager.decryptMessage(partnerAddress, encryptedBytes)
                                    if (decryptedResult.isSuccess) {
                                        val decryptedBytes = decryptedResult.getOrThrow()
                                        val reEncrypted = cryptoManager.encryptLocal(decryptedBytes)
                                        storyDao.upsertStory(
                                            StatusStoryEntity(
                                                id = story.storyId,
                                                authorId = partnerId,
                                                contentType = story.contentType,
                                                encryptedPayload = reEncrypted,
                                                backgroundColor = story.backgroundColor,
                                                expiresAt = story.expiresAt,
                                                createdAt = story.createdAt,
                                                isFromMe = false
                                            )
                                        )
                                    } else {
                                        android.util.Log.e("StoryVM", "Failed to decrypt story message: ${decryptedResult.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StoryVM", "Error parsing incoming story share", e)
                                }
                            }
                        }
                        "STORY_VIEWED" -> {
                            msg.payload?.let { payload ->
                                android.util.Log.d("StoryVM", "Partner viewed story: $payload")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun postStory(text: String, bgColor: String) {
        viewModelScope.launch {
            try {
                val storyId = UUID.randomUUID().toString()
                val textBytes = text.toByteArray(Charsets.UTF_8)
                val localEncrypted = cryptoManager.encryptLocal(textBytes)
                val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                val partnerEncryptedResult = cryptoManager.encryptMessage(partnerAddress, textBytes)
                if (partnerEncryptedResult.isFailure) {
                    android.util.Log.e("StoryVM", "Failed to encrypt story for partner using Double Ratchet")
                    return@launch
                }
                val partnerEncryptedB64 = android.util.Base64.encodeToString(partnerEncryptedResult.getOrThrow(), android.util.Base64.NO_WRAP)
                val expiresAt = System.currentTimeMillis() + 86_400_000L

                val entity = StatusStoryEntity(
                    id = storyId,
                    authorId = myId,
                    contentType = "TEXT",
                    encryptedPayload = localEncrypted,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    isFromMe = true
                )
                storyDao.upsertStory(entity)

                val sharePayload = StorySharePayload(
                    storyId = storyId,
                    contentType = "TEXT",
                    encryptedContent = partnerEncryptedB64,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    createdAt = entity.createdAt
                )
                val msg = SignalMessageWrapper(
                    type = "STORY_SHARE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(sharePayload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
            } catch (e: Exception) {
                android.util.Log.e("StoryVM", "postStory failed", e)
            }
        }
    }

    fun postMediaStory(mediaBytes: ByteArray, contentType: String, bgColor: String = "#000000") {
        viewModelScope.launch {
            try {
                val storyId = UUID.randomUUID().toString()
                val localEncrypted = cryptoManager.encryptLocal(mediaBytes)
                val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                val partnerEncryptedResult = cryptoManager.encryptMessage(partnerAddress, mediaBytes)
                if (partnerEncryptedResult.isFailure) {
                    android.util.Log.e("StoryVM", "Failed to encrypt media story for partner")
                    return@launch
                }
                val partnerEncryptedB64 = android.util.Base64.encodeToString(partnerEncryptedResult.getOrThrow(), android.util.Base64.NO_WRAP)
                val expiresAt = System.currentTimeMillis() + 86_400_000L

                val entity = StatusStoryEntity(
                    id = storyId,
                    authorId = myId,
                    contentType = contentType,
                    encryptedPayload = localEncrypted,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    isFromMe = true
                )
                storyDao.upsertStory(entity)

                val sharePayload = StorySharePayload(
                    storyId = storyId,
                    contentType = contentType,
                    encryptedContent = partnerEncryptedB64,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    createdAt = entity.createdAt
                )
                val msg = SignalMessageWrapper(
                    type = "STORY_SHARE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(sharePayload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
            } catch (e: Exception) {
                android.util.Log.e("StoryVM", "postMediaStory failed", e)
            }
        }
    }

    fun decryptStory(entity: StatusStoryEntity): String {
        return try {
            String(cryptoManager.decryptLocal(entity.encryptedPayload), Charsets.UTF_8)
        } catch (_: Exception) {
            "🔒 Encrypted Story"
        }
    }

    fun decryptMedia(entity: StatusStoryEntity): ByteArray? {
        return try {
            cryptoManager.decryptLocal(entity.encryptedPayload)
        } catch (_: Exception) {
            null
        }
    }

    fun markViewed(storyId: String) {
        viewModelScope.launch {
            storyDao.markViewed(storyId, System.currentTimeMillis())
            // Notify partner
            val msg = SignalMessageWrapper(
                type = "STORY_VIEWED",
                senderId = myId,
                targetId = partnerId,
                payload = storyId
            )
            signalingClient.sendRawMessage(Json.encodeToString(msg))
        }
    }

    fun deleteMyStory(storyId: String) {
        viewModelScope.launch { storyDao.deleteStory(storyId) }
    }
}

// ─── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun StatusStoriesScreen(viewModel: StoryViewModel) {
    val myStories by viewModel.myStories.collectAsState()
    val partnerStories by viewModel.partnerStories.collectAsState()

    var showCompose by remember { mutableStateOf(false) }
    var selectedStory by remember { mutableStateOf<StatusStoryEntity?>(null) }

    BackHandler(enabled = showCompose || selectedStory != null) {
        if (selectedStory != null) {
            selectedStory = null
        } else if (showCompose) {
            showCompose = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F6))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Story rings header row
            item {
                Column {
                    Text(
                        text = "Status",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF2A1B1D)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        // My story ring
                        item {
                            StoryRing(
                                label = "My Story",
                                hasStory = myStories.isNotEmpty(),
                                hasUnviewed = false,
                                isMe = true,
                                initial = "ME",
                                onClick = {
                                    if (myStories.isEmpty()) showCompose = true
                                    else selectedStory = myStories.first()
                                }
                            )
                        }
                        // Partner story ring
                        if (partnerStories.isNotEmpty()) {
                            item {
                                StoryRing(
                                    label = "Partner",
                                    hasStory = true,
                                    hasUnviewed = partnerStories.any { it.viewedAt == 0L },
                                    isMe = false,
                                    initial = "P",
                                    onClick = { selectedStory = partnerStories.first() }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFFCE2E6), thickness = 1.dp)
                }
            }

            // Recent stories list
            if (myStories.isEmpty() && partnerStories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💫", fontSize = 56.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No stories yet", fontWeight = FontWeight.SemiBold, color = Color(0xFF2A1B1D), fontSize = 16.sp)
                            Text("Tap + to share a 24-hour status story", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            items(myStories) { story ->
                StoryListItem(story, isMe = true, onTap = { selectedStory = story })
            }
            items(partnerStories) { story ->
                StoryListItem(story, isMe = false, onTap = {
                    viewModel.markViewed(story.id)
                    selectedStory = story
                })
            }
        }

        // FAB to compose new story
        FloatingActionButton(
            onClick = { showCompose = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Color(0xFFE598A7),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Story")
        }
    }

    // Compose story sheet
    if (showCompose) {
        ComposeStorySheet(
            onDismiss = { showCompose = false },
            onPost = { text, color ->
                viewModel.postStory(text, color)
                showCompose = false
            },
            onPostMedia = { bytes, type ->
                viewModel.postMediaStory(bytes, type)
                showCompose = false
            }
        )
    }

    // Story viewer overlay
    selectedStory?.let { story ->
        StoryViewerOverlay(
            story = story,
            decryptedText = viewModel.decryptStory(story),
            isMe = story.authorId == viewModel.myId,
            viewModel = viewModel,
            onClose = { selectedStory = null },
            onDelete = {
                viewModel.deleteMyStory(story.id)
                selectedStory = null
            }
        )
    }
}

@Composable
private fun StoryRing(
    label: String,
    hasStory: Boolean,
    hasUnviewed: Boolean,
    isMe: Boolean,
    initial: String,
    onClick: () -> Unit
) {
    val ringColor = when {
        !hasStory -> Color(0xFFE0E0E0)
        hasUnviewed -> Color(0xFFE598A7)
        else -> Color(0xFF90CAF9)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .drawBehind {
                    if (hasStory) {
                        drawCircle(
                            color = ringColor.copy(alpha = if (hasUnviewed) ringAlpha else 0.7f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                .padding(5.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFF5C6CF), Color(0xFFE598A7))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isMe && !hasStory) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(initial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF2A1B1D), maxLines = 1)
    }
}

@Composable
private fun StoryListItem(
    story: StatusStoryEntity,
    isMe: Boolean,
    onTap: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = fmt.format(Date(story.createdAt))
    val expiryPct = ((story.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L).toFloat()
        / 86_400_000f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress arc avatar
        Box(modifier = Modifier.size(52.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFE598A7),
                    startAngle = -90f,
                    sweepAngle = 360f * expiryPct,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(1.5f, 1.5f),
                    size = Size(size.width - 3f, size.height - 3f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(story.backgroundColor))),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isMe) "ME" else "P", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "Your Story" else "Partner's Story",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF2A1B1D)
            )
            Text(
                text = "$timeStr · ${if (story.viewedAt > 0) "Viewed" else "Not viewed"}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        if (story.viewedAt == 0L && !isMe) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE598A7))
            )
        }
    }
}

@Composable
private fun ComposeStorySheet(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit,
    onPostMedia: (ByteArray, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(STORY_COLORS.first()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bytes = compressImage(context, uri)
                    onPostMedia(bytes, "IMAGE")
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Error compressing image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bytes = getVideoBytesAndVerifyDuration(context, uri)
                    onPostMedia(bytes, "VIDEO")
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, e.message ?: "Video processing error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Status Story", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(android.graphics.Color.parseColor(selectedColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text.ifBlank { "Your story…" },
                        color = if (selectedColor == "#2A1B1D") Color.White else Color(0xFF2A1B1D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 200) text = it },
                    placeholder = { Text("What's on your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE598A7),
                        unfocusedBorderColor = Color(0xFFFCE2E6)
                    )
                )

                // Background color picker
                Text("Background", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(STORY_COLORS) { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (hex == selectedColor) 3.dp else 0.dp,
                                    color = Color(0xFFE598A7),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            pickImageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE598A7)),
                        border = BorderStroke(1.dp, Color(0xFFE598A7))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Image", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            pickVideoLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE598A7)),
                        border = BorderStroke(1.dp, Color(0xFFE598A7))
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Video (≤60s)", fontSize = 11.sp)
                    }
                }

                Text("Stories disappear after 24 hours · E2EE encrypted", fontSize = 10.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onPost(text, selectedColor) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
            ) {
                Text("Share Story", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFFFFF5F6)
    )
}

@Composable
private fun StoryViewerOverlay(
    story: StatusStoryEntity,
    decryptedText: String,
    isMe: Boolean,
    viewModel: StoryViewModel,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val anim = remember { Animatable(0f) }
    var viewerDuration by remember { mutableStateOf(5000L) }

    LaunchedEffect(story, viewerDuration) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(viewerDuration.toInt(), easing = LinearEasing))
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(android.graphics.Color.parseColor(story.backgroundColor)))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        // Media or Text display
        if (story.contentType == "TEXT") {
            Text(
                text = decryptedText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (story.backgroundColor == "#2A1B1D") Color.White else Color(0xFF2A1B1D),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        } else if (story.contentType == "IMAGE") {
            val decryptedBytes = remember(story) { viewModel.decryptMedia(story) }
            val bitmap = remember(decryptedBytes) {
                decryptedBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Text("Error displaying image", color = Color.White)
            }
        } else if (story.contentType == "VIDEO") {
            val context = androidx.compose.ui.platform.LocalContext.current
            var tempFile by remember { mutableStateOf<java.io.File?>(null) }

            LaunchedEffect(story) {
                try {
                    val decryptedBytes = viewModel.decryptMedia(story)
                    if (decryptedBytes != null) {
                        val file = java.io.File.createTempFile("story_", ".mp4", context.cacheDir)
                        file.writeBytes(decryptedBytes)
                        tempFile = file

                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val durationMs = durationStr?.toLongOrNull() ?: 5000L
                            viewerDuration = durationMs
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        } finally {
                            retriever.release()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    tempFile?.let { file ->
                        secureShred(file)
                    }
                }
            }

            tempFile?.let { file ->
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            LinearProgressIndicator(
                progress = { anim.value },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.8f),
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }

        // Close & delete
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isMe) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF2A1B1D))
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF2A1B1D))
            }
        }
    }
}

// ─── HELPER FUNCTIONS FOR MEDIA AND SECURITY ──────────────────────────────────

fun compressImage(context: android.content.Context, uri: android.net.Uri): ByteArray {
    val inputStream = context.contentResolver.openInputStream(uri)
    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    val maxDimension = 640
    val width = originalBitmap.width
    val height = originalBitmap.height
    val (newWidth, newHeight) = if (width > height) {
        val ratio = height.toFloat() / width
        maxDimension to (maxDimension * ratio).toInt()
    } else {
        val ratio = width.toFloat() / height
        (maxDimension * ratio).toInt() to maxDimension
    }

    val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
    val outputStream = java.io.ByteArrayOutputStream()
    resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
    val bytes = outputStream.toByteArray()
    originalBitmap.recycle()
    resizedBitmap.recycle()
    return bytes
}

fun getVideoBytesAndVerifyDuration(context: android.content.Context, uri: android.net.Uri): ByteArray {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L
        if (durationMs > 60000L) {
            throw IllegalArgumentException("Video exceeds 60 seconds limit")
        }
    } finally {
        retriever.release()
    }

    val inputStream = context.contentResolver.openInputStream(uri)
    val bytes = inputStream?.readBytes() ?: throw java.io.IOException("Failed to read video bytes")
    inputStream.close()
    return bytes
}

fun secureShred(file: java.io.File) {
    if (file.exists()) {
        try {
            val length = file.length()
            val random = java.security.SecureRandom()
            val zeros = ByteArray(1024)
            val randomBytes = ByteArray(1024)

            java.io.RandomAccessFile(file, "rwd").use { raf ->
                // Pass 1: Zero out
                raf.seek(0)
                var written = 0L
                while (written < length) {
                    val writeLen = Math.min(zeros.size.toLong(), length - written).toInt()
                    raf.write(zeros, 0, writeLen)
                    written += writeLen
                }

                // Pass 2: Random bytes
                raf.seek(0)
                written = 0L
                while (written < length) {
                    val writeLen = Math.min(randomBytes.size.toLong(), length - written).toInt()
                    random.nextBytes(randomBytes)
                    raf.write(randomBytes, 0, writeLen)
                    written += writeLen
                }

                // Pass 3: Zero out again
                raf.seek(0)
                written = 0L
                while (written < length) {
                    val writeLen = Math.min(zeros.size.toLong(), length - written).toInt()
                    raf.write(zeros, 0, writeLen)
                    written += writeLen
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            file.delete()
        }
    }
}
