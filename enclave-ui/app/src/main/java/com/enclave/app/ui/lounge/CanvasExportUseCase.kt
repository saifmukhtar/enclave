package com.enclave.app.ui.lounge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CanvasExportUseCase {
    suspend fun generateCanvasBitmapBytes(
        localStrokes: List<LoungeStroke>,
        partnerStrokes: List<LoungeStroke>
    ): ByteArray {
        return withContext(Dispatchers.Default) {
            val size = 800
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }

            fun drawStrokeToBitmap(stroke: LoungeStroke) {
                if (stroke.points.isEmpty()) return
                try {
                    paint.color = android.graphics.Color.parseColor(stroke.colorHex)
                } catch (e: Exception) {
                    paint.color = android.graphics.Color.BLACK
                }
                paint.strokeWidth = stroke.brushWidth

                val path = android.graphics.Path()
                path.moveTo(stroke.points[0].x * size, stroke.points[0].y * size)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x * size, stroke.points[i].y * size)
                }
                canvas.drawPath(path, paint)
            }

            val allStrokes = localStrokes + partnerStrokes
            allStrokes.forEach { drawStrokeToBitmap(it) }

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            val bytes = outputStream.toByteArray()
            bitmap.recycle()
            bytes
        }
    }
}
