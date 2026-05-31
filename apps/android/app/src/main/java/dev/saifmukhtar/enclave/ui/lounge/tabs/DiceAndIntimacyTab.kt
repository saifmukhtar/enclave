@file:OptIn(ExperimentalMaterial3Api::class)
package dev.saifmukhtar.enclave.ui.lounge.tabs

import dev.saifmukhtar.enclave.ui.lounge.*
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

// ==========================================
// 3. 🎲 3D Tumbling Dice & Intimacy Prompts Tab
// ==========================================
@Composable
fun DiceAndIntimacyTab(
    
    loungeGamesFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    val gamesViewModel: dev.saifmukhtar.enclave.ui.lounge.LoungeGamesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeGamesFactory)

    val isRolling by gamesViewModel.isDiceRolling.collectAsState()
    val diceValue by gamesViewModel.diceValue.collectAsState()
    val currentPrompt by gamesViewModel.currentPrompt.collectAsState()
    val isTruthSelected by gamesViewModel.isTruthSelected.collectAsState()

    val haptic = LocalView.current

    // Trigger local clock-tick haptics coordinated with tumbling frames
    LaunchedEffect(Unit) {
        gamesViewModel.diceTickerEvent.collect {
            haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // 3D rotation animation states
    val rotationX by animateFloatAsState(
        targetValue = if (isRolling) 720f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing)
    )
    val rotationY by animateFloatAsState(
        targetValue = if (isRolling) 720f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tumbling dice display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer {
                            this.rotationX = rotationX
                            this.rotationY = rotationY
                            this.cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFF5F6))
                        .clickable { gamesViewModel.rollDice() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = diceValue.toString(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE598A7)
                    )
                }
            }
        }

        // Intimacy Truth or Dare SWIPER prompts
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isTruthSelected) "Intimacy Truth Capsule" else "Intimacy Dare Challenge",
                    fontWeight = FontWeight.Bold,
                    color = if (isTruthSelected) Color(0xFFE598A7) else Color(0xFF2A1B1D),
                    fontSize = 14.sp
                )

                Text(
                    text = currentPrompt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2A1B1D),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { gamesViewModel.pickTruthOrDareCard(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF5F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cozy Truth", color = Color(0xFFE598A7), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { gamesViewModel.pickTruthOrDareCard(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Intimate Dare", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

