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
// 4. 🖌️ Live Shared Canvas Tab
// ==========================================
@Composable
fun LiveSharedCanvasTab(viewModel: LoungeViewModel) {
    val localStrokes = viewModel.localStrokes
    val partnerStrokes = viewModel.partnerStrokes
    val activeLocalStroke by viewModel.currentLocalStroke.collectAsState()
    val activePartnerStroke by viewModel.currentPartnerStroke.collectAsState()

    val drawings by viewModel.loungeDrawings.collectAsState()
    val isDrawingUploading by viewModel.isDrawingUploading.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }

    var drawColorHex by remember { mutableStateOf("#E598A7") } // Pink default local
    var brushWidth by remember { mutableStateOf(8f) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#E598A7", "#FF5722", "#4CAF50", "#2196F3", "#9C27B0", "#FFEB3B", "#000000").forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (drawColorHex == hex) 2.dp else 0.dp,
                                color = if (drawColorHex == hex) Color.DarkGray else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { drawColorHex = hex }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.clearCanvas() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1B1D)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Clear Canvas", color = Color.White, fontSize = 11.sp)
                }

                Button(
                    onClick = { showSaveDialog = true },
                    enabled = !isDrawingUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isDrawingUploading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 1.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Saving...", color = Color.White, fontSize = 11.sp)
                    } else {
                        Text("Save 💾", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { drawColorHex = "#FFFFFF" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (drawColorHex == "#FFFFFF") Color(0xFFE598A7) else Color(0xFF2A1B1D)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Eraser 🧼", color = Color.White, fontSize = 11.sp)
            }

            Text("Size:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            listOf(
                "Thin" to 4f,
                "Medium" to 8f,
                "Thick" to 16f,
                "Jumbo" to 32f
            ).forEach { (label, size) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (brushWidth == size) Color(0xFFFCE2E6) else Color.Transparent)
                        .clickable { brushWidth = size }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 10.sp, color = if (brushWidth == size) Color(0xFFE598A7) else Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // use full remaining height
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            viewModel.startLocalStroke(offset.x / size.width.toFloat(), offset.y / size.height.toFloat(), drawColorHex, brushWidth)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            viewModel.addLocalStrokePoint(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                        },
                        onDragEnd = {
                            viewModel.finalizeLocalStroke()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                fun drawStroke(points: List<LoungePoint>, colorHex: String, width: Float) {
                    if (points.isEmpty()) return
                    if (points.size == 1) {
                        drawCircle(
                            color = Color(android.graphics.Color.parseColor(colorHex)),
                            radius = width / 2,
                            center = androidx.compose.ui.geometry.Offset(points[0].x * w, points[0].y * h)
                        )
                        return
                    }
                    val path = Path()
                    path.moveTo(points[0].x * w, points[0].y * h)
                    for (i in 1 until points.size - 1) {
                        val midX = (points[i].x + points[i + 1].x) / 2 * w
                        val midY = (points[i].y + points[i + 1].y) / 2 * h
                        path.quadraticBezierTo(points[i].x * w, points[i].y * h, midX, midY)

                    }
                    // Connect to the final point
                    path.lineTo(points.last().x * w, points.last().y * h)
                    drawPath(
                        path = path,
                        color = Color(android.graphics.Color.parseColor(colorHex)),
                        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                localStrokes.forEach { drawStroke(it.points, it.colorHex, it.brushWidth) }
                activeLocalStroke?.let { drawStroke(it.points, it.colorHex, it.brushWidth) }
                partnerStrokes.forEach { drawStroke(it.points, it.colorHex, it.brushWidth) }
                activePartnerStroke?.let { drawStroke(it.points, it.colorHex, it.brushWidth) }

            }
        }



        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Sketch to Vault") },
                text = {
                    OutlinedTextField(
                        value = saveTitle,
                        onValueChange = { saveTitle = it },
                        label = { Text("Sketch Title") },
                        placeholder = { Text("e.g. Happy Flowers") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (saveTitle.isNotBlank()) {
                                viewModel.saveCanvasToGallery(saveTitle)
                                showSaveDialog = false
                                saveTitle = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE598A7))
                    ) {
                        Text("Save", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}


