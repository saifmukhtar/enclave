package com.enclave.app.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.chat.ChatMessage
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.theme.CharcoalText
import com.enclave.app.ui.theme.RoseAccent

@Composable
fun ImageMessageContent(
    message: ChatMessage,
    viewModel: ChatViewModel,
    onMediaClick: () -> Unit
) {
    var bitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(message.id) {
        val bytes = viewModel.getMediaBytes(message.id)
        if (bytes != null) {
            bitmapState = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        isLoading = false
    }

    DisposableEffect(message.id) {
        onDispose { bitmapState = null }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(CharcoalText.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = CharcoalText.copy(alpha = 0.3f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        bitmapState?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Decrypted Image Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onMediaClick() }
            )
        } ?: Text("Failed to decrypt image", color = CharcoalText, fontSize = 13.sp)
    }
}

@Composable
fun VideoMessageContent(onMediaClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalText.copy(alpha = 0.1f))
            .clickable { onMediaClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = "Play Video",
            tint = RoseAccent,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Play Video",
            color = CharcoalText.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

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
            imageVector = Icons.Default.InsertDriveFile,
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
