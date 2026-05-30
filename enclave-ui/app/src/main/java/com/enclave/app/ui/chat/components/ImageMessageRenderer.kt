package com.enclave.app.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.chat.ChatMessage
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.theme.CharcoalText
import com.enclave.app.ui.theme.RoseAccent
import com.enclave.app.ui.theme.RoseDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageMessageContent(
    message: ChatMessage,
    viewModel: ChatViewModel,
    isSelectionMode: Boolean,
    onMediaClick: () -> Unit
) {
    val cachedBitmap = remember(message.id) { viewModel.getCachedBitmap(message.id) }
    var bitmapState by remember(message.id) { mutableStateOf(cachedBitmap) }
    var isLoading by remember(message.id) { mutableStateOf(bitmapState == null) }

    LaunchedEffect(message.id) {
        if (bitmapState == null) {
            bitmapState = viewModel.getMediaBitmap(message.id)
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(CharcoalText.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = if (message.isFromMe) Color.White else RoseDeep,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        bitmapState?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Decrypted Image Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .then(
                        if (!isSelectionMode) {
                            Modifier.clickable { onMediaClick() }
                        } else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    if (message.isFromMe) Color.Black.copy(alpha = 0.12f)
                    else CharcoalText.copy(alpha = 0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (message.isFromMe) Color.White else RoseDeep,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Failed to decrypt image",
                    color = if (message.isFromMe) Color.White else CharcoalText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─── Video bubble with async thumbnail ───────────────────────────────────────

@Composable
fun VideoMessageContent(
    message: ChatMessage,
    viewModel: ChatViewModel,
    isSelectionMode: Boolean,
    onMediaClick: () -> Unit
) {
    val cachedThumb = remember(message.id) { viewModel.getCachedBitmap(message.id, isThumbnail = true) }
    var thumbBitmap by remember(message.id) { mutableStateOf(cachedThumb) }

    LaunchedEffect(message.id) {
        if (thumbBitmap == null) {
            thumbBitmap = viewModel.getMediaBitmap(message.id, isThumbnail = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .then(
                if (!isSelectionMode) {
                    Modifier.clickable { onMediaClick() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background — thumbnail or dark fallback
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = "Video thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A0810))
            )
        }

        // Soft dark gradient scrim for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )

        // Play button circle
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White.copy(alpha = 0.90f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = Color(0xFFD84B6F),
                modifier = Modifier.size(34.dp)
            )
        }

        // "Video" label at bottom-left
        Text(
            text = "Video",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 4f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
        )
    }
}

// ─── Generic file attachment ──────────────────────────────────────────────────

@Composable
fun FileMessageContent(onMediaClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalText.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onMediaClick() }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = "Document",
            tint = RoseAccent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "📄 Document",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = CharcoalText
        )
    }
}
