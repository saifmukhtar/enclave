@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.lounge.tabs

import com.enclave.app.ui.lounge.*
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

// ==========================================
// 5. 🫣 Touch-Activated Scratch-to-Reveal / View-Once Tab
// ==========================================
@Composable
fun ScratchToRevealTab(
    
    loungeGamesFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    val gamesViewModel: com.enclave.app.ui.lounge.LoungeGamesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeGamesFactory)
    val scratchState by gamesViewModel.scratchState.collectAsState()
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes != null) {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    
                    var inSampleSize = 1
                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    if (maxDim > 640) {
                        inSampleSize = Math.round(maxDim.toFloat() / 640f)
                    }
                    
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    
                    if (decodedBitmap != null) {
                        val scaledBitmap = if (decodedBitmap.width > 640 || decodedBitmap.height > 640) {
                            val ratio = decodedBitmap.width.toFloat() / decodedBitmap.height.toFloat()
                            val (targetWidth, targetHeight) = if (ratio > 1) {
                                640 to (640 / ratio).toInt()
                            } else {
                                (640 * ratio).toInt() to 640
                            }
                            android.graphics.Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
                        } else {
                            decodedBitmap
                        }
                        
                        val outputStream = java.io.ByteArrayOutputStream()
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        gamesViewModel.sendScratchImage(compressedBytes)
                    } else {
                        gamesViewModel.sendScratchImage(bytes)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load photo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (scratchState == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF5F6)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Upload Secret Photo",
                tint = Color(0xFFE598A7),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Share a Secret Photo",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1B1D)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your partner will scratch to reveal it. Self-destructs in 10 seconds of active touching.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Select from Gallery", color = Color.White)
            }
        }
    } else {
        val state = scratchState!!

        if (state.isSender) {
            // SENDER UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF5F6)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val icon = if (state.isDestroyed) Icons.Default.DeleteForever else if (state.isSeen) Icons.Default.Visibility else Icons.Default.CheckCircle
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    tint = Color(0xFFE598A7),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (state.isDestroyed) "Photo Shredded!" else if (state.isSeen) "Partner is Revealing..." else "Secret Photo Sent!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A1B1D)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (state.isDestroyed) "Your partner has viewed and shredded the photo." else if (state.isSeen) "Your partner is currently scratching the photo." else "Waiting for your partner to reveal the photo.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        gamesViewModel.clearScratchImage()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.isDestroyed) "Send Another Secret" else "Cancel & Recall", color = Color.White)
                }
            }
        } else {
            // RECEIVER UI
            val bitmap = remember(state.bytes) {
                try {
                    android.graphics.BitmapFactory.decodeByteArray(state.bytes, 0, state.bytes.size)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            var remainingTimeMs by remember { mutableStateOf(10000L) }
            var isSelfDestructed by remember { mutableStateOf(false) }
            val scratchPath = remember { androidx.compose.ui.graphics.Path() }
            var drawTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var isTouching by remember { mutableStateOf(false) }

            LaunchedEffect(state.bytes) {
                remainingTimeMs = 10000L
                isSelfDestructed = false
                scratchPath.reset()
                drawTrigger++
                isTouching = false
            }

            LaunchedEffect(isTouching, isSelfDestructed) {
                if (isTouching && !isSelfDestructed) {
                    while (remainingTimeMs > 0 && isTouching) {
                        delay(50L)
                        remainingTimeMs -= 50
                    }
                    if (remainingTimeMs <= 0) {
                        isSelfDestructed = true
                        gamesViewModel.notifyScratchDestroyed()
                        gamesViewModel.clearScratchImage()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isSelfDestructed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Shredded",
                            tint = Color(0xFFE598A7),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Love Capsule Shredded!", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Self-destructed successfully after 10s view.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                gamesViewModel.clearScratchImage()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Secret Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(16.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color(0xFFFCE2E6), Color(0xFFE598A7)),
                                            center = center,
                                            radius = size.minDimension / 1.5f
                                        )
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "💋 Intimacy Card",
                                fontSize = 24.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (!state.isSeen) {
                                            gamesViewModel.notifyScratchSeen()
                                        }
                                        isTouching = true
                                        scratchPath.moveTo(offset.x, offset.y)
                                        drawTrigger++
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        scratchPath.lineTo(change.position.x, change.position.y)
                                        drawTrigger++
                                    },
                                    onDragEnd = {
                                        isTouching = false
                                    },
                                    onDragCancel = {
                                        isTouching = false
                                    }
                                )
                            }
                    ) {
                        drawTrigger // Read state to trigger redraw without recomposing parent
                        drawRect(color = Color(0xFFE598A7))

                        drawPath(
                            path = scratchPath,
                            color = Color.Transparent,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 90.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            ),
                            blendMode = BlendMode.Clear
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⌛ Remaining: ${(remainingTimeMs / 1000f).coerceAtLeast(0f)}s",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { gamesViewModel.clearScratchImage() },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

