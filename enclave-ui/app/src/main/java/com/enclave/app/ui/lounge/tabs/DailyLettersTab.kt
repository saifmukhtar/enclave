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
// 2. 💌 Daily Decrypted In-Memory Letters Tab
// ==========================================
@Composable
fun DailyLettersTab(loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory) {
    val viewModel: com.enclave.app.ui.lounge.LoungeMediaViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loungeMediaFactory)
    val letters by viewModel.decryptedLettersFlow.collectAsState(initial = emptyList())
    var newLetterText by remember { mutableStateOf("") }
    

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Write New Daily Capsule Letter", fontWeight = FontWeight.Bold, color = Color(0xFF2A1B1D), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newLetterText,
                    onValueChange = { newLetterText = it },
                    placeholder = { Text("Type something intimate, sweet or hidden...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE598A7),
                        unfocusedBorderColor = Color(0xFFFCE2E6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newLetterText.isNotBlank()) {
                            viewModel.sendLetter(newLetterText)
                            newLetterText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Secure Capsule and Send", color = Color.White)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(letters, key = { it.id }) { letter ->
                var isDecrypted by remember(letter.id) { mutableStateOf(false) }
                var letterText by remember(letter.id) { mutableStateOf("🔒 Securely Encrypted Daily Capsule") }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (letter.senderId == viewModel.myId) "You Sent" else "Received Partner",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = letterText,
                                fontSize = 13.sp,
                                color = Color(0xFF2A1B1D)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.deleteLetter(letter.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                            if (!isDecrypted) {
                                IconButton(
                                    onClick = {
                                        letterText = viewModel.decryptLetter(letter.ciphertext)
                                        isDecrypted = true
                                        viewModel.markLetterAsRead(letter.id)
                                    }
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Decrypt", tint = Color(0xFFE598A7))
                                }
                            } else {
                                Icon(Icons.Default.LockOpen, contentDescription = "Decrypted", tint = Color(0xFF4CAF50), modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

