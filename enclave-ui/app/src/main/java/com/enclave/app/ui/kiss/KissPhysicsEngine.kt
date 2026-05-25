package com.enclave.app.ui.kiss

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.enclave.app.ui.kiss.physics.LipPhysicsEngine

// ─── DrawScope extension: Lip Physics Mesh ────────────────────────────────────

@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawLipPhysicsMesh(
    engine: LipPhysicsEngine,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    pressure: Float = 0.5f,
    orientation: Float = 0f,
    canvasWidth: Float
) {
    if (engine.nodes.isEmpty()) return

    val p = pressure.coerceIn(0.01f, 1f)
    val baseAlpha = alpha * (0.35f + p * 0.65f)
    val drawingScale = (canvasWidth * 0.6f) / 4.5f

    withTransform({
        rotate(degrees = Math.toDegrees(orientation.toDouble()).toFloat(), pivot = Offset(cx, cy))
    }) {
        // 1. Smooth Bezier contour fill (outer boundary nodes 0..141)
        val path = Path()
        val startNode = engine.nodes.find { it.id == 0 }
        if (startNode != null) {
            val startX = cx + startNode.currentX * drawingScale
            val startY = cy + startNode.currentY * drawingScale
            path.moveTo(startX, startY)
            var prevX = startX; var prevY = startY
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                val nextX = cx + node.currentX * drawingScale
                val nextY = cy + node.currentY * drawingScale
                val midX = (prevX + nextX) / 2f
                val midY = (prevY + nextY) / 2f
                path.quadraticBezierTo(prevX, prevY, midX, midY)
                prevX = nextX; prevY = nextY
            }
            path.close()
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = baseAlpha * 0.5f), color.copy(alpha = baseAlpha * 0.05f)),
                    center = Offset(cx, cy),
                    radius = drawingScale * 1.5f
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 2. Sharp perimeter rim
        val perimeterPath = Path()
        val pStart = engine.nodes.find { it.id == 0 }
        if (pStart != null) {
            perimeterPath.moveTo(cx + pStart.currentX * drawingScale, cy + pStart.currentY * drawingScale)
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                perimeterPath.lineTo(cx + node.currentX * drawingScale, cy + node.currentY * drawingScale)
            }
            perimeterPath.close()
            drawPath(
                path = perimeterPath,
                color = color.copy(alpha = baseAlpha * 0.8f),
                style = Stroke(
                    width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 3. Inner springs as glowing wireframe
        for (spring in engine.springs) {
            val n1 = spring.node1; val n2 = spring.node2
            drawLine(
                color = color.copy(alpha = baseAlpha * 0.15f),
                start = Offset(cx + n1.currentX * drawingScale, cy + n1.currentY * drawingScale),
                end = Offset(cx + n2.currentX * drawingScale, cy + n2.currentY * drawingScale),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ─── DrawScope extension: Lip Impression (pressure-driven organic stamp) ──────

/**
 * Organic touch-impression renderer.
 *
 * Draws a pressure-driven irregular stamp of micro-blobs + grain lines
 * so every touch looks natural and unique to the motion/pressure applied.
 */
fun DrawScope.drawLipImpression(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    glowRadius: Float = 0f,
    pressure: Float = 0.5f,
    touchMajor: Float = 0f,
    touchMinor: Float = 0f,
    orientation: Float = 0f
) {
    val p = pressure.coerceIn(0.01f, 1f)
    val pigment = Color(
        red = color.red + (1f - color.red) * (1f - p) * 0.35f,
        green = color.green + (1f - color.green) * (1f - p) * 0.35f,
        blue = color.blue + (1f - color.blue) * (1f - p) * 0.35f,
        alpha = 1f
    )
    val baseAlpha = alpha * (0.32f + p * 0.68f)
    val baseWidth = if (touchMajor > 0f) touchMajor else radius * 2.2f
    val baseHeight = if (touchMinor > 0f) touchMinor else radius * 1.4f
    val finalWidth = baseWidth * (0.9f + p * 0.2f)
    val finalHeight = baseHeight * (0.9f + p * 0.2f)

    withTransform({
        rotate(degrees = Math.toDegrees(orientation.toDouble()).toFloat(), pivot = Offset(cx, cy))
    }) {
        if (glowRadius > 0f) {
            val gw = (if (touchMajor > 0f) touchMajor * 1.25f else glowRadius * 2.2f) * (0.9f + p * 0.2f)
            val gh = (if (touchMinor > 0f) touchMinor * 1.25f else glowRadius * 1.4f) * (0.9f + p * 0.2f)
            drawOval(
                color = pigment.copy(alpha = baseAlpha * 0.12f),
                topLeft = Offset(cx - gw * 0.5f, cy - gh * 0.5f),
                size = Size(gw, gh),
                blendMode = BlendMode.Screen
            )
        }

        val microBlobCount = (6 + p * 20f).toInt().coerceIn(6, 28)
        for (i in 0 until microBlobCount) {
            val t = i.toFloat() / microBlobCount.toFloat()
            val ang = t * (Math.PI * 2.0) + (cx * 0.0013f + cy * 0.0017f + i * 0.37f)
            val rJitter = 0.15f + ((i * 73) % 100) / 170f
            val x = cx + kotlin.math.cos(ang).toFloat() * (finalWidth * 0.5f) * rJitter
            val y = cy + kotlin.math.sin(ang).toFloat() * (finalHeight * 0.5f) * rJitter * (0.82f + p * 0.36f)
            val dot = radius * (0.16f + ((i * 31) % 100) / 520f) * (0.7f + p * 0.6f)
            drawCircle(
                color = pigment.copy(alpha = baseAlpha * (0.45f + (i % 5) * 0.08f).coerceAtMost(0.95f)),
                radius = dot, center = Offset(x, y), blendMode = BlendMode.Screen
            )
        }

        val grainLines = (8 + p * 32f).toInt().coerceIn(8, 40)
        val spanX = finalWidth * 0.55f
        val spanY = finalHeight * 0.55f
        for (i in 0 until grainLines) {
            val yy = cy - spanY * 0.5f + (i / grainLines.toFloat()) * spanY
            val wobble = kotlin.math.sin((i * 0.73f + cx * 0.002f).toDouble()).toFloat() * finalHeight * 0.04f
            drawLine(
                color = pigment.copy(alpha = baseAlpha * 0.20f),
                start = Offset(cx - spanX * 0.5f, yy + wobble),
                end = Offset(cx + spanX * 0.5f, yy - wobble * 0.6f),
                strokeWidth = (radius * 0.028f * (0.45f + p)).coerceAtLeast(0.7f),
                cap = StrokeCap.Round, blendMode = BlendMode.Screen
            )
        }
    }
}
