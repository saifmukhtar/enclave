@file:OptIn(ExperimentalMaterial3Api::class)
package dev.saifmukhtar.enclave.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import dev.saifmukhtar.enclave.ui.profile.components.ComposeStorySheet
import dev.saifmukhtar.enclave.ui.profile.components.StoryViewerOverlay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.crypto.CryptoManager
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase
import dev.saifmukhtar.enclave.data.local.StatusStoryEntity
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.LenientJson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

// ─── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun StatusStoriesScreen(viewModel: StoryViewModel) {
    val myStories by viewModel.myStories.collectAsState()
    val partnerStories by viewModel.partnerStories.collectAsState()

    var showCompose by remember { mutableStateOf(false) }
    var selectedStory by remember { mutableStateOf<StatusStoryEntity?>(null) }

    BackHandler(enabled = showCompose || selectedStory != null) {
        if (selectedStory != null) {
            selectedStory = null
        } else if (showCompose) {
            showCompose = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F6))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Story rings header row
            item {
                Column {
                    Text(
                        text = "Status",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF2A1B1D)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        // My story ring
                        item {
                            StoryRing(
                                label = "My Story",
                                hasStory = myStories.isNotEmpty(),
                                hasUnviewed = false,
                                isMe = true,
                                initial = "ME",
                                onClick = {
                                    if (myStories.isEmpty()) showCompose = true
                                    else selectedStory = myStories.first()
                                }
                            )
                        }
                        // Partner story ring
                        if (partnerStories.isNotEmpty()) {
                            item {
                                StoryRing(
                                    label = "Partner",
                                    hasStory = true,
                                    hasUnviewed = partnerStories.any { it.viewedAt == 0L },
                                    isMe = false,
                                    initial = "P",
                                    onClick = { selectedStory = partnerStories.first() }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFFCE2E6), thickness = 1.dp)
                }
            }

            // Recent stories list
            if (myStories.isEmpty() && partnerStories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💫", fontSize = 56.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No stories yet", fontWeight = FontWeight.SemiBold, color = Color(0xFF2A1B1D), fontSize = 16.sp)
                            Text("Tap + to share a 24-hour status story", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            items(myStories) { story ->
                StoryListItem(story, isMe = true, onTap = { selectedStory = story })
            }
            items(partnerStories) { story ->
                StoryListItem(story, isMe = false, onTap = {
                    viewModel.markViewed(story.id)
                    selectedStory = story
                })
            }
        }

        // FAB to compose new story
        FloatingActionButton(
            onClick = { showCompose = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Color(0xFFE598A7),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Story")
        }
    }

    // Compose story sheet
    if (showCompose) {
        ComposeStorySheet(
            onDismiss = { showCompose = false },
            onPost = { text, color ->
                viewModel.postStory(text, color)
                showCompose = false
            },
            onPostMedia = { bytes, type ->
                viewModel.postMediaStory(bytes, type)
                showCompose = false
            }
        )
    }

    // Story viewer overlay
    selectedStory?.let { story ->
        StoryViewerOverlay(
            story = story,
            decryptedText = viewModel.decryptStory(story),
            isMe = story.authorId == viewModel.myId,
            viewModel = viewModel,
            onClose = { selectedStory = null },
            onDelete = {
                viewModel.deleteMyStory(story.id)
                selectedStory = null
            }
        )
    }
}

@Composable
private fun StoryRing(
    label: String,
    hasStory: Boolean,
    hasUnviewed: Boolean,
    isMe: Boolean,
    initial: String,
    onClick: () -> Unit
) {
    val ringColor = when {
        !hasStory -> Color(0xFFE0E0E0)
        hasUnviewed -> Color(0xFFE598A7)
        else -> Color(0xFF90CAF9)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .drawBehind {
                    if (hasStory) {
                        drawCircle(
                            color = ringColor.copy(alpha = if (hasUnviewed) ringAlpha else 0.7f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                .padding(5.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFF5C6CF), Color(0xFFE598A7))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isMe && !hasStory) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(initial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF2A1B1D), maxLines = 1)
    }
}

@Composable
private fun StoryListItem(
    story: StatusStoryEntity,
    isMe: Boolean,
    onTap: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = fmt.format(Date(story.createdAt))
    val expiryPct = ((story.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L).toFloat()
        / 86_400_000f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress arc avatar
        Box(modifier = Modifier.size(52.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xFFE598A7),
                    startAngle = -90f,
                    sweepAngle = 360f * expiryPct,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(1.5f, 1.5f),
                    size = Size(size.width - 3f, size.height - 3f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(story.backgroundColor))),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isMe) "ME" else "P", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "Your Story" else "Partner's Story",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF2A1B1D)
            )
            Text(
                text = "$timeStr · ${if (story.viewedAt > 0) "Viewed" else "Not viewed"}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        if (story.viewedAt == 0L && !isMe) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE598A7))
            )
        }
    }
}


