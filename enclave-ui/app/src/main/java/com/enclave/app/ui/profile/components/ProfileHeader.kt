package com.enclave.app.ui.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileHeader(
    avatarBitmap: ImageBitmap?,
    editDisplayName: String,
    editUsername: String,
    selectedMood: String,
    onAvatarClick: () -> Unit,
    onMoodClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blushAccent = Color(0xFFE598A7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF5C6CF), Color(0xFFFFF5F6))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(blushAccent, Color(0xFFF5C6CF)))
                )
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = (editDisplayName.ifBlank { editUsername })
                    .take(2).uppercase().ifBlank { "ME" }
                Text(initials, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            // Small overlay to indicate change is possible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Change Avatar",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Mood badge overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 36.dp, y = (-8).dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onMoodClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(selectedMood, fontSize = 16.sp)
        }
    }
}
