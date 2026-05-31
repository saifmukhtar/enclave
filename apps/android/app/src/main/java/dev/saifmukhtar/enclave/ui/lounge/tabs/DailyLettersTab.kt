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
import androidx.compose.material.icons.automirrored.filled.Send
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
import dev.saifmukhtar.enclave.ui.theme.PlayfairFont
import dev.saifmukhtar.enclave.ui.theme.InterFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.foundation.BorderStroke
import java.io.File

// ==========================================
// 2. 💌 Daily Decrypted In-Memory Letters Tab
// ==========================================
@Composable
fun DailyLettersTab(loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory) {
    val viewModel: dev.saifmukhtar.enclave.ui.lounge.LoungeMediaViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeMediaFactory)
    val letters by viewModel.decryptedLettersFlow.collectAsState(initial = emptyList())
    var newLetterText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F2)),
            border = BorderStroke(1.dp, Color(0xFFFFE4E8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null,
                        tint = dev.saifmukhtar.enclave.ui.theme.RoseDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Write Daily Capsule Letter",
                        fontWeight = FontWeight.ExtraBold,
                        color = dev.saifmukhtar.enclave.ui.theme.CharcoalText,
                        fontSize = 16.sp,
                        fontFamily = PlayfairFont
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newLetterText,
                    onValueChange = { newLetterText = it },
                    placeholder = { Text("Type something intimate, sweet or hidden...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = dev.saifmukhtar.enclave.ui.theme.RoseAccent,
                        unfocusedBorderColor = Color(0xFFFFE4E8)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (newLetterText.isNotBlank()) {
                            viewModel.sendLetter(newLetterText)
                            newLetterText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dev.saifmukhtar.enclave.ui.theme.RoseDeep),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Capsule and Send", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(letters, key = { it.id }) { letter ->
                var isDecrypted by remember(letter.id) { mutableStateOf(false) }
                var letterText by remember(letter.id) { mutableStateOf("🔒 Securely Encrypted Daily Capsule") }

                val rotation = remember { Animatable(0f) }
                val coroutineScope = rememberCoroutineScope()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFFFE4E8)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left accent strip indicating sender state
                        val accentStripBrush = Brush.verticalGradient(
                            colors = if (letter.senderId == viewModel.myId) {
                                listOf(dev.saifmukhtar.enclave.ui.theme.RoseAccent, dev.saifmukhtar.enclave.ui.theme.RoseDeep)
                            } else {
                                listOf(Color(0xFFB5A8E0), Color(0xFF8B7CC8))
                            }
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(96.dp)
                                .background(accentStripBrush)
                        )

                        val myProfile by viewModel.myProfile.collectAsState()
                        val partnerProfile by viewModel.partnerProfile.collectAsState()
                        val myUsername = myProfile?.username?.ifBlank { null } ?: "You"
                        val partnerUsername = partnerProfile?.username?.ifBlank { null } ?: "Partner"

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (letter.senderId == viewModel.myId) "From $myUsername" else "From $partnerUsername",
                                    fontSize = 12.sp,
                                    color = dev.saifmukhtar.enclave.ui.theme.RoseDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = PlayfairFont,
                                    modifier = Modifier.graphicsLayer(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                AnimatedContent(
                                    targetState = letterText,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                        fadeOut(animationSpec = tween(90))
                                    },
                                    label = "decrypt_text"
                                ) { targetText ->
                                    Text(
                                        text = targetText,
                                        fontSize = 15.sp,
                                        color = dev.saifmukhtar.enclave.ui.theme.CharcoalText,
                                        fontFamily = if (isDecrypted) PlayfairFont else InterFont
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { viewModel.deleteLetter(letter.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                }
                                if (!isDecrypted) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                rotation.animateTo(
                                                    targetValue = 360f,
                                                    animationSpec = tween(
                                                        durationMillis = 600,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                                letterText = viewModel.decryptLetter(letter.ciphertext)
                                                isDecrypted = true
                                                viewModel.markLetterAsRead(letter.id)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Decrypt",
                                            tint = dev.saifmukhtar.enclave.ui.theme.RoseDeep,
                                            modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Decrypted",
                                        tint = Color(0xFF52B788),
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

