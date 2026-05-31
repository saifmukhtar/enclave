package dev.saifmukhtar.enclave.ui.profile.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saifmukhtar.enclave.data.local.StatusStoryEntity
import dev.saifmukhtar.enclave.ui.profile.StoryViewModel
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

@Composable
fun StoryViewerOverlay(
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
            var tempFile by remember { mutableStateOf<File?>(null) }

            LaunchedEffect(story) {
                try {
                    val decryptedBytes = viewModel.decryptMedia(story)
                    if (decryptedBytes != null) {
                        val file = File.createTempFile("story_", ".mp4", context.cacheDir)
                        file.writeBytes(decryptedBytes)
                        tempFile = file

                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val durationMs = durationStr?.toLongOrNull() ?: 5000L
                            viewerDuration = durationMs
                        } catch (ex: Exception) {
                            android.util.Log.e("Enclave", "Exception caught", ex)
                        } finally {
                            retriever.release()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Enclave", "Exception caught", e)
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

fun secureShred(file: File) {
    if (file.exists()) {
        try {
            val length = file.length()
            val random = SecureRandom()
            val zeros = ByteArray(1024)
            val randomBytes = ByteArray(1024)

            RandomAccessFile(file, "rwd").use { raf ->
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
            android.util.Log.e("Enclave", "Exception caught", e)
        } finally {
            file.delete()
        }
    }
}
