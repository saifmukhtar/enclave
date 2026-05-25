package com.enclave.app.ui.lounge.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun VinylRecordRenderer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Infinite rotation transition for the vinyl record
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_infinite")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )
    val vinylAngle = if (isPlaying) rotationAngle else 0f

    // Pivoting tonearm needle angle animation
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 25f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "tonearm_angle"
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // A. Rotating Vinyl Body
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer(rotationZ = vinylAngle)
        ) {
            // Vinyl outer disc
            drawCircle(color = Color(0xFF1C1A1A), radius = size.minDimension / 2)
            
            // Groove lines (concentric circles)
            val totalGrooves = 8
            val radiusMax = size.minDimension / 2
            for (i in 1..totalGrooves) {
                val r = radiusMax * (0.4f + 0.5f * (i.toFloat() / totalGrooves))
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = r,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Vinyl label center (Blush colored)
            drawCircle(color = Color(0xFFE598A7), radius = radiusMax * 0.35f)
            
            // Stylized label design (inner circle)
            drawCircle(color = Color(0xFF2A1B1D), radius = radiusMax * 0.12f)
            
            // Center hole
            drawCircle(color = Color(0xFFFFF5F6), radius = radiusMax * 0.04f)
        }

        // B. Pivoting Tonearm overlay
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    rotationZ = tonearmAngle
                    // Rotate around top-right pivot anchor point
                    transformOrigin = TransformOrigin(0.85f, 0.15f)
                }
        ) {
            val w = size.width
            val h = size.height
            val pivotX = w * 0.85f
            val pivotY = h * 0.15f

            // Arm base circle
            drawCircle(
                color = Color(0xFF8E8E93),
                radius = 12.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color(0xFF3A3A3C),
                radius = 6.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )

            // The tonearm rod line (straight arm ending near center)
            val endX = w * 0.42f
            val endY = h * 0.52f
            drawLine(
                color = Color(0xFFC7C7CC),
                start = Offset(pivotX, pivotY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Headshell (cartridge housing at the stylus tip)
            drawRect(
                color = Color(0xFF3A3A3C),
                topLeft = Offset(endX - 8.dp.toPx(), endY - 6.dp.toPx()),
                size = Size(16.dp.toPx(), 12.dp.toPx())
            )
        }
    }
}
