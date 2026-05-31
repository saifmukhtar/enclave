@file:OptIn(ExperimentalMaterial3Api::class)
package dev.saifmukhtar.enclave.ui.lounge

import dev.saifmukhtar.enclave.ui.lounge.tabs.*

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import dev.saifmukhtar.enclave.ui.theme.PlayfairFont
import dev.saifmukhtar.enclave.ui.theme.InterFont
import dev.saifmukhtar.enclave.data.local.LetterEntity
import dev.saifmukhtar.enclave.media.MusicSyncController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.foundation.BorderStroke
import java.io.File


@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun LoungeScreen(
    viewModel: LoungeViewModel,
    profileViewModel: dev.saifmukhtar.enclave.ui.profile.ProfileViewModel? = null,
    @Suppress("UNUSED_PARAMETER") musicSyncController: MusicSyncController?,
    chatViewModel: dev.saifmukhtar.enclave.ui.chat.ChatViewModel? = null,
    loungeGamesFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeDrawingFactory: androidx.lifecycle.ViewModelProvider.Factory,
    @Suppress("UNUSED_PARAMETER") loungeMusicFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    var activeTab by remember { mutableStateOf("hub") }

    BackHandler(enabled = activeTab != "hub") {
        activeTab = "hub"
    }

    AnimatedContent(
        targetState = activeTab,
        transitionSpec = {
            if (targetState == "hub") {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            } else {
                (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
            }
        },
        label = "LoungeNavigation"
    ) { currentView ->
        if (currentView == "hub") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF5F6))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Wave Canvas Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, size.height * 0.75f)
                                cubicTo(
                                    size.width * 0.75f, size.height * 0.95f,
                                    size.width * 0.25f, size.height * 0.55f,
                                    0f, size.height * 0.85f
                                )
                                close()
                            }
                            drawPath(
                                path = path,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF7C5D1), Color(0xFFFFF5F6))
                                )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Enclave Lounge",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dev.saifmukhtar.enclave.ui.theme.CharcoalText,
                                    fontFamily = PlayfairFont
                                )
                                
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse_heart")
                                val heartScale by infiniteTransition.animateFloat(
                                    initialValue = 1.0f,
                                    targetValue = 1.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(900, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "heart"
                                )
                                Text(
                                    text = "❤️",
                                    fontSize = 20.sp,
                                    modifier = Modifier.scale(heartScale)
                                )
                            }
                            Text(
                                text = "Your intimate shared companion spaces",
                                color = dev.saifmukhtar.enclave.ui.theme.CharcoalText.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = PlayfairFont,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    val tabs = listOf(
                        Triple("profiles", "Profiles", "E2EE Data") to (Icons.Default.Person to (Color(0xFFE598A7) to Color(0xFFD4607A))),
                        Triple("notes", "Notes", "Shared Ideas") to (Icons.Default.EditNote to (Color(0xFFB5A8E0) to Color(0xFF8B7CC8))),
                        Triple("letters", "Letters", "Daily Capsules") to (Icons.Default.Mail to (Color(0xFFF4A261) to Color(0xFFE76F51))),
                        Triple("dice", "Games", "Play Together") to (Icons.Default.Casino to (Color(0xFF52B788) to Color(0xFF2D6A4F))),
                        Triple("canvas", "Live Draw", "Shared Canvas") to (Icons.Default.Edit to (Color(0xFF4ECDC4) to Color(0xFF2AA8A0))),
                        Triple("secret", "Reveal", "Secret Photos") to (Icons.Default.VisibilityOff to (Color(0xFF9B5DE5) to Color(0xFF7B2FBE))),
                        Triple("scrapbook", "Scrapbook", "Photo Albums") to (Icons.Default.PhotoLibrary to (Color(0xFFFFA600) to Color(0xFFE68200))),
                        Triple("timeline", "Timeline", "Memory Lane") to (Icons.Default.History to (Color(0xFF6495ED) to Color(0xFF4169E1)))
                    )

                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(tabs.size) { index ->
                            val (info, data) = tabs[index]
                            val (id, title, subtitle) = info
                            val (icon, gradientColors) = data
                            val (gStart, gEnd) = gradientColors

                            val isCardPressed = remember { mutableStateOf(false) }
                            val cardScale by animateFloatAsState(
                                targetValue = if (isCardPressed.value) 0.94f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "card_lounge_scale"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.0f)
                                    .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isCardPressed.value = true
                                                try {
                                                    awaitRelease()
                                                } finally {
                                                    isCardPressed.value = false
                                                }
                                            },
                                            onTap = { activeTab = id }
                                        )
                                    },
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(gStart, gEnd)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = dev.saifmukhtar.enclave.ui.theme.CharcoalText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = PlayfairFont
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        fontSize = 12.sp,
                                        color = dev.saifmukhtar.enclave.ui.theme.CharcoalText.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Detail View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF5F6))
            ) {
                // Top App Bar for Detail View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFFFE4E8), CircleShape)
                            .clickable { activeTab = "hub" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = dev.saifmukhtar.enclave.ui.theme.CharcoalText)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    val formattedTitle = when (activeTab) {
                        "profiles" -> "Partner Profiles"
                        "notes" -> "Shared Notes"
                        "letters" -> "Daily Letters"
                        "dice" -> "Intimate Games"
                        "canvas" -> "Shared Canvas"
                        "secret" -> "Secret Photos"
                        "scrapbook" -> "Scrapbook Albums"
                        "timeline" -> "Memory Timeline"
                        else -> activeTab.replaceFirstChar { it.uppercase() }
                    }

                    Text(
                        text = formattedTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlayfairFont,
                        color = dev.saifmukhtar.enclave.ui.theme.CharcoalText
                    )
                }

                HorizontalDivider(color = Color(0xFFFFE4E8), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (currentView) {
                        "profiles" -> ProfileCardsTab(viewModel, profileViewModel)
                        "notes" -> E2EENotesTab(loungeMediaFactory)
                        "letters" -> DailyLettersTab(loungeMediaFactory)
                        "dice" -> DiceAndIntimacyTab(loungeGamesFactory)
                        "canvas" -> LiveSharedCanvasTab(loungeDrawingFactory)
                        "secret" -> ScratchToRevealTab(loungeGamesFactory)
                        "scrapbook" -> ScrapbookTab(loungeMediaFactory)
                        "timeline" -> MemoryTimelineTab(chatViewModel)
                    }
                }
            }
        }
    }
}


