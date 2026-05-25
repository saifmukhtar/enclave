package com.enclave.app.ui.lounge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.lounge.tabs.CircularCountdownUnit

@Composable
fun CountdownCard(
    targetLabel: String,
    isExpired: Boolean,
    days: Long,
    hours: Long,
    minutes: Long,
    seconds: Long,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Shared Visit Countdown", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                    Text(
                        text = if (targetLabel.isNotEmpty()) targetLabel else "Set countdown to keep track of next meeting!",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Timer, contentDescription = "Edit timer", tint = Color(0xFFE598A7))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isExpired) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF5F6))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (targetLabel.isNotEmpty()) "Countdown expired! 💕" else "No countdown active",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE598A7)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularCountdownUnit(
                        value = days,
                        label = "Days",
                        progress = if (days > 30) 1.0f else days / 30.0f
                    )
                    CircularCountdownUnit(
                        value = hours,
                        label = "Hours",
                        progress = hours / 24.0f
                    )
                    CircularCountdownUnit(
                        value = minutes,
                        label = "Mins",
                        progress = minutes / 60.0f
                    )
                    CircularCountdownUnit(
                        value = seconds,
                        label = "Secs",
                        progress = seconds / 60.0f
                    )
                }
            }
        }
    }
}
