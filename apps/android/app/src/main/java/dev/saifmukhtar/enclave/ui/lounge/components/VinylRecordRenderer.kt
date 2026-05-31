package dev.saifmukhtar.enclave.ui.lounge.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
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
    var currentRotation by remember { mutableStateOf(0f) }
    val targetSpeed = if (isPlaying) 2.2f else 0f
    val speed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "vinyl_speed"
    )

    LaunchedEffect(Unit) {
        while (true) {
            if (speed > 0.01f) {
                currentRotation = (currentRotation + speed) % 360f
            }
            kotlinx.coroutines.delay(16)
        }
    }
    val vinylAngle = currentRotation

    // Pivoting tonearm needle angle animation with spring bounce landing!
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f, // Tactile bouncy pivot
            stiffness = Spring.StiffnessLow
        ),
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
