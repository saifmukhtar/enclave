package com.enclave.app.ui.lounge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.media.MusicSyncController

@Composable
fun MusicPlayerControls(
    musicSyncController: MusicSyncController,
    currentTrackName: String,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    sleepTimerRemaining: Int,
    modifier: Modifier = Modifier
) {
    var sleepTimerDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (currentTrackName.isNotEmpty()) currentTrackName else "Select a Track",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1B1D),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Playback progress slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { pct ->
                        val targetMs = (pct * duration).toLong()
                        musicSyncController.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE598A7),
                        activeTrackColor = Color(0xFFE598A7),
                        inactiveTrackColor = Color(0xFFFCE2E6)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPosition), fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(formatDuration(duration), fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            }

            // Full controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { musicSyncController.toggleShuffle() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) Color(0xFFE598A7) else Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = { musicSyncController.playPrevious() }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color(0xFFE598A7),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play/Pause Button
                IconButton(
                    onClick = { musicSyncController.togglePlayPause() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFFF5F6), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFFE598A7),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = { musicSyncController.playNext() }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color(0xFFE598A7),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Sleep Timer Trigger
                Box {
                    IconButton(
                        onClick = { sleepTimerDropdownExpanded = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemaining > 0) Color(0xFFE598A7) else Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sleepTimerDropdownExpanded,
                        onDismissRequest = { sleepTimerDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Off") },
                            onClick = {
                                musicSyncController.setSleepTimer(0)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("15 min") },
                            onClick = {
                                musicSyncController.setSleepTimer(15)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("30 min") },
                            onClick = {
                                musicSyncController.setSleepTimer(30)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("60 min") },
                            onClick = {
                                musicSyncController.setSleepTimer(60)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("120 min") },
                            onClick = {
                                musicSyncController.setSleepTimer(120)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("180 min") },
                            onClick = {
                                musicSyncController.setSleepTimer(180)
                                sleepTimerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Sleep Timer countdown chip
            if (sleepTimerRemaining > 0) {
                val timerMinutes = sleepTimerRemaining / 60
                val timerSeconds = sleepTimerRemaining % 60
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = String.format("Sleep in %02d:%02d 😴", timerMinutes, timerSeconds),
                            color = Color(0xFFE598A7)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFFFFF5F6)
                    )
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
