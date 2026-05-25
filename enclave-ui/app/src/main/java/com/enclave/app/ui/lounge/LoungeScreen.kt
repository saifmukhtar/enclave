@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.lounge

import com.enclave.app.ui.lounge.tabs.*

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.data.local.LetterEntity
import com.enclave.app.media.MusicSyncController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.foundation.BorderStroke
import java.io.File


@Composable
fun LoungeScreen(
    viewModel: LoungeViewModel,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel? = null,
    @Suppress("UNUSED_PARAMETER") musicSyncController: MusicSyncController?,
    chatViewModel: com.enclave.app.ui.chat.ChatViewModel? = null,
    loungeGamesFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeDrawingFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeMusicFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    var activeTab by remember { mutableStateOf("profiles") }

    BackHandler(enabled = activeTab != "profiles") {
        activeTab = "profiles"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF5F6)) // Minimalist Blush palette canvas
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant Glowing Header (Blend with theme, no hard boundary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💋 Enclave Shared Lounge",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A1B1D),
                    fontFamily = FontFamily.Serif
                )
            }

            // Minimalist Category Chips Category Row (Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple("profiles", "✨ Profiles", Icons.Default.Person),
                    Triple("notes", "📝 Notes", Icons.Default.EditNote),
                    Triple("letters", "💌 Letters", Icons.Default.Mail),
                    Triple("dice", "🎲 Games", Icons.Default.Casino),
                    Triple("canvas", "🖌️ Live Draw", Icons.Default.Edit),
                    Triple("secret", "🫣 Reveal", Icons.Default.VisibilityOff),
                    Triple("quiz", "❤️ Quiz", Icons.Default.Favorite),
                    Triple("scrapbook", "📸 Scrapbook", Icons.Default.PhotoLibrary),
                    Triple("timeline", "📅 Timeline", Icons.Default.History),
                    Triple("music", "🎵 Music", Icons.Default.MusicNote)
                )

                tabs.forEach { (id, label, icon) ->
                    val isSelected = activeTab == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFFE598A7) else Color(0xFFFCE2E6))
                            .clickable { activeTab = id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Color(0xFF2A1B1D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color(0xFF2A1B1D)
                            )
                        }
                    }
                }
            }

            // Primary Screen Content Viewports
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    "profiles" -> ProfileCardsTab(viewModel, profileViewModel)
                    "notes" -> E2EENotesTab(loungeMediaFactory)
                    "letters" -> DailyLettersTab(loungeMediaFactory)
                    "dice" -> DiceAndIntimacyTab(loungeGamesFactory)
                    "canvas" -> LiveSharedCanvasTab(loungeDrawingFactory)
                    "secret" -> ScratchToRevealTab(loungeGamesFactory)
                    "quiz" -> LoveLanguageQuizTab(loungeGamesFactory)
                    "scrapbook" -> ScrapbookTab(loungeMediaFactory)
                    "timeline" -> MemoryTimelineTab(chatViewModel)
                    "music" -> MusicLoungeTab(musicSyncController, loungeMusicFactory)
                }
            }
        }
    }
}


