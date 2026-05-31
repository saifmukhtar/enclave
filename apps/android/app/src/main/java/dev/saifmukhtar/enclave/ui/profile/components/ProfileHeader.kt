package dev.saifmukhtar.enclave.ui.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
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

    val infiniteTransition = rememberInfiniteTransition(label = "orbital_ring")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotate"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbital_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF5C6CF), Color(0xFFFFF5F6))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Orbital outer spinner ring
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(pulseScale)
                .drawBehind {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(blushAccent, Color(0xFFD4607A), blushAccent)
                        ),
                        startAngle = rotationAngle,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(30f, 15f),
                                phase = 0f
                            )
                        )
                    )
                },
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
