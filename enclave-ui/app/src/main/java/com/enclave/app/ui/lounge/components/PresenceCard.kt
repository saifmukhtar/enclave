package com.enclave.app.ui.lounge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.profile.components.E2eeAvatar
import com.enclave.app.ui.profile.ProfileViewModel
import com.enclave.app.ui.theme.PlayfairFont
import com.enclave.app.ui.theme.InterFont

@Composable
fun PresenceCard(
    isMe: Boolean,
    name: String,
    username: String,
    avatarLocalPath: String?,
    avatarUrl: String?,
    bio: String?,
    statusText: String,
    moodEmoji: String,
    batteryPct: Int,
    weatherTemp: Double,
    weatherCondition: String,
    nowListening: String,
    localTimeStr: String = "",
    profileViewModel: ProfileViewModel?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(260.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name.uppercase(),
                    fontSize = 12.sp,
                    fontFamily = PlayfairFont,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) Color(0xFFE598A7) else Color(0xFF2A1B1D),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (username.isNotBlank()) {
                    Text(
                        text = username,
                        fontSize = 9.sp,
                        fontFamily = InterFont,
                        color = Color.Gray.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFF5F6), Color(0xFFE598A7), Color(0xFFFFF5F6))
                            )
                        )
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                    ) {
                        E2eeAvatar(
                            avatarBase64 = if (isMe) avatarLocalPath else avatarUrl,
                            isMe = isMe,
                            profileViewModel = profileViewModel,
                            initials = name.take(2),
                            size = 64.dp,
                            displayName = name,
                            username = username.removePrefix("@"),
                            bio = bio,
                            statusText = statusText,
                            enablePreview = true
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFFFF5F6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(moodEmoji.ifBlank { "❤️" }, fontSize = 12.sp)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(statusText, fontWeight = FontWeight.Bold, fontFamily = InterFont, color = Color(0xFF2A1B1D), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(2.dp))
                val weatherStr = if (weatherTemp != -999.0) {
                    " • $weatherCondition ${weatherTemp.toInt()}°C"
                } else ""
                
                val timeStr = if (!isMe && localTimeStr.isNotEmpty()) " • 🕒 $localTimeStr" else ""
                Text("🔋 Battery: $batteryPct%$timeStr$weatherStr", fontSize = 10.sp, fontFamily = InterFont, color = Color.Gray)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isMe) Color(0xFFFFF5F6) else Color(0xFFFCE2E6))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = "Listen", tint = if (isMe) Color(0xFFE598A7) else Color(0xFF2A1B1D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(nowListening, fontSize = 9.sp, fontFamily = InterFont, color = Color(0xFF2A1B1D), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
