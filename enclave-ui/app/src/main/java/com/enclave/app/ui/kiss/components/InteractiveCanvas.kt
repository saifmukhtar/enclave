package com.enclave.app.ui.kiss.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * High-performance Compose Canvas layered over the WebRTC stream.
 * Includes 16ms throttling for WebSocket synchronization.
 */
@Composable
fun InteractiveCanvas(
    onPathDrawn: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current drawing path
    val paths = remember { mutableStateListOf<List<Offset>>() }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    // Throttling mechanism
    var lastSendTime by remember { mutableStateOf(0L) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath = listOf(offset)
                    },
                    onDragEnd = {
                        paths.add(currentPath)
                        currentPath = emptyList()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPath = currentPath + change.position
                        
                        // 16ms throttling (~60fps) to prevent WebSocket flooding
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSendTime >= 16) {
                            onPathDrawn(currentPath)
                            lastSendTime = currentTime
                        }
                    }
                )
            }
    ) {
        // Draw all completed paths
        for (p in paths) {
            if (p.size > 1) {
                val composePath = Path().apply {
                    moveTo(p.first().x, p.first().y)
                    for (i in 1 until p.size) {
                        lineTo(p[i].x, p[i].y)
                    }
                }
                drawPath(
                    path = composePath,
                    color = Color(0xFFFF1493), // Neon Kiss pink
                    style = Stroke(
                        width = 15f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        
        // Draw the path currently being dragged
        if (currentPath.size > 1) {
            val composePath = Path().apply {
                moveTo(currentPath.first().x, currentPath.first().y)
                for (i in 1 until currentPath.size) {
                    lineTo(currentPath[i].x, currentPath[i].y)
                }
            }
            drawPath(
                path = composePath,
                color = Color(0xFFFF1493),
                style = Stroke(
                    width = 15f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
