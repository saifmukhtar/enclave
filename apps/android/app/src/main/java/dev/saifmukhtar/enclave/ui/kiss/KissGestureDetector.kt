package dev.saifmukhtar.enclave.ui.kiss

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import dev.saifmukhtar.enclave.models.KissGestureFrame
import dev.saifmukhtar.enclave.models.RecordedKissPoint

// ─── Touch aggregation helpers ──────────────────────────────────────────────────

data class AggregatedImpression(
    val xPct: Float,
    val yPct: Float,
    val pressure: Float,
    val radiusPx: Float,
    val touchMajor: Float,
    val touchMinor: Float,
    val orientation: Float
)

fun List<KissGestureFrame>.toAggregatedImpression(): AggregatedImpression? {
    if (isEmpty()) return null
    val avgX = map { it.xPct }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val avgY = map { it.yPct }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val sumPressure = map { it.pressure }.filterNot { it.isNaN() }.sum()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1.2f)
    val maxRadius = map { it.touchRadius }.filterNot { it.isNaN() }.maxOrNull() ?: 22f
    val maxMajor = map { it.touchMajor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val maxMinor = map { it.touchMinor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val avgOrientation = map { it.orientation }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }

    val spread = if (size > 1) {
        map {
            val dx = it.xPct - avgX
            val dy = it.yPct - avgY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }.filterNot { it.isNaN() }.average()
            .let { if (it.isNaN()) 0f else it.toFloat() } * 420f
    } else 0f

    val cohesiveRadius = (maxRadius * 0.88f + spread + sumPressure * 18f)
        .let { if (it.isNaN()) 22f else it }.coerceIn(22f, 120f)
    return AggregatedImpression(
        xPct = avgX, yPct = avgY, pressure = sumPressure, radiusPx = cohesiveRadius,
        touchMajor = if (maxMajor > 0f) maxMajor else cohesiveRadius * 2.2f,
        touchMinor = if (maxMinor > 0f) maxMinor else cohesiveRadius * 1.4f,
        orientation = avgOrientation
    )
}

fun isIntentionalImpressionTouch(pressure: Float, wasActive: Boolean): Boolean {
    if (wasActive) return true
    return pressure >= 0.045f
}

fun getRawMotionEvent(event: androidx.compose.ui.input.pointer.PointerEvent): android.view.MotionEvent? {
    try {
        val field = event.javaClass.getDeclaredFields()
            .firstOrNull { it.type == android.view.MotionEvent::class.java }
        if (field != null) {
            field.isAccessible = true
            return field.get(event) as? android.view.MotionEvent
        }
        val method = event.javaClass.getDeclaredMethods()
            .firstOrNull { it.returnType == android.view.MotionEvent::class.java }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(event) as? android.view.MotionEvent
        }
    } catch (e: Exception) {
        android.util.Log.e("Enclave", "Exception caught", e)
    }
    return null
}

// ─── Gesture Detector ─────────────────────────────────────────────────────────

suspend fun PointerInputScope.detectKissGestures(
    recordButtonBounds: Rect?,
    closeButtonBounds: Rect?,
    haptic: HapticFeedback,
    isRecordingKiss: Boolean,
    recordingStartTime: Long,
    onImpressionClear: () -> Unit,
    onImpressionUpdate: (AggregatedImpression) -> Unit,
    onRecordPoints: (List<RecordedKissPoint>) -> Unit,
    onCanvasSize: (androidx.compose.ui.geometry.Size) -> Unit
) {
    val activePointersMap = mutableMapOf<Long, KissGestureFrame>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            onCanvasSize(androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))

            val activePointers = event.changes.filter { it.pressed }
            if (activePointers.isEmpty()) {
                onImpressionClear()
                activePointersMap.clear()
                event.changes.forEach { it.consume() }
                continue
            }

            event.changes.forEach { change ->
                val pos = change.position
                val inRecord = recordButtonBounds?.contains(pos) == true
                val inClose = closeButtonBounds?.contains(pos) == true
                if (inRecord || inClose) return@forEach

                if (change.pressed) {
                    if (!change.previousPressed) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    val pressure = change.pressure.coerceIn(0f, 1.2f)
                    val wasActive = activePointersMap.containsKey(change.id.value)
                    if (!isIntentionalImpressionTouch(pressure, wasActive)) return@forEach

                    val motionEvent = getRawMotionEvent(event)
                    var pointerIndex = -1
                    if (motionEvent != null) {
                        for (i in 0 until motionEvent.pointerCount) {
                            val xDiff = Math.abs(motionEvent.getX(i) - change.position.x)
                            val yDiff = Math.abs(motionEvent.getY(i) - change.position.y)
                            if (xDiff < 25f && yDiff < 25f) {
                                pointerIndex = i; break
                            }
                        }
                    }
                    val touchSize = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getSize(pointerIndex) else 0.05f
                    val touchMajor = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getTouchMajor(pointerIndex) else 0f
                    val touchMinor = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getTouchMinor(pointerIndex) else 0f
                    val orientation = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getOrientation(pointerIndex) else 0f
                    val touchRadius = (22f + touchSize * 750f).coerceIn(18f, 150f)

                    val xPctVal = if (size.width > 0) change.position.x / size.width.toFloat() else 0f
                    val yPctVal = if (size.height > 0) change.position.y / size.height.toFloat() else 0f

                    val frame = KissGestureFrame(
                        xPct = if (xPctVal.isNaN()) 0f else xPctVal,
                        yPct = if (yPctVal.isNaN()) 0f else yPctVal,
                        action = "KISS_TOUCH_MOVE",
                        pressure = pressure.coerceIn(0f, 1f),
                        touchRadius = touchRadius,
                        fingerIndex = change.id.value.toInt(),
                        touchMajor = touchMajor,
                        touchMinor = touchMinor,
                        orientation = orientation
                    )
                    activePointersMap[change.id.value] = frame
                    change.consume()
                } else {
                    activePointersMap.remove(change.id.value)
                    change.consume()
                }
            }

            val allFrames = activePointersMap.values.toList()
            if (allFrames.isNotEmpty()) {
                val agg = allFrames.toAggregatedImpression()
                if (agg != null) {
                    onImpressionUpdate(agg)
                }
                if (isRecordingKiss) {
                    val offset = System.currentTimeMillis() - recordingStartTime
                    val pts = allFrames.map { p ->
                        RecordedKissPoint(
                            xPct = p.xPct, yPct = p.yPct,
                            pressure = p.pressure, touchRadius = p.touchRadius,
                            timeOffsetMs = offset
                        )
                    }
                    onRecordPoints(pts)
                }
            } else {
                onImpressionClear()
                activePointersMap.clear()
            }
        }
    }
}
