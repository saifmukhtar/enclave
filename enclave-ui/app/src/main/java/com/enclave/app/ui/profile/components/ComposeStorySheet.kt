package com.enclave.app.ui.profile.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.profile.STORY_COLORS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeStorySheet(
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
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = compressImage(context, uri)
                    onPostMedia(bytes, "IMAGE")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
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
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = getVideoBytesAndVerifyDuration(context, uri)
                    onPostMedia(bytes, "VIDEO")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
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
