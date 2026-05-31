package dev.saifmukhtar.enclave.ui.lounge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import dev.saifmukhtar.enclave.ui.theme.PlayfairFont
import dev.saifmukhtar.enclave.ui.theme.InterFont

@Composable
fun HeartbeatCard(
    isHoldingHeartbeat: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    heartbeatScale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Tactile Heartbeat Synchronizer", fontWeight = FontWeight.Bold, fontFamily = PlayfairFont, color = Color(0xFF2A1B1D), fontSize = 14.sp)
            Text("Hold the heart to transmit your real-time vital sign pulse to your partner.", fontFamily = InterFont, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(heartbeatScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF5F6), Color(0xFFFCE2E6))
                        )
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                onHoldStart()
                                waitForUpOrCancellation()
                                onHoldEnd()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Pulse heart",
                    tint = if (isHoldingHeartbeat) Color.Red else Color(0xFFE598A7),
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}
