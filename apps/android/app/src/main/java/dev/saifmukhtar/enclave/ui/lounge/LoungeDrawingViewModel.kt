package dev.saifmukhtar.enclave.ui.lounge

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.network.BundleRepository
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LoungeDrawingViewModel(
    private val signalingClient: SignalingClient,
    private val bundleRepository: BundleRepository?,
    private val partnerId: String,
    val myId: String,
    private val loungeSyncUseCase: LoungeSyncUseCase
) : ViewModel() {

    // --- Live Drawing Canvas States ---
    val localStrokes = mutableStateListOf<LoungeStroke>()
    val partnerStrokes = mutableStateListOf<LoungeStroke>()

    private val _currentLocalStroke = MutableStateFlow<LoungeStroke?>(null)
    val currentLocalStroke: StateFlow<LoungeStroke?> = _currentLocalStroke.asStateFlow()

    private val _currentPartnerStroke = MutableStateFlow<LoungeStroke?>(null)
    val currentPartnerStroke: StateFlow<LoungeStroke?> = _currentPartnerStroke.asStateFlow()

    private val currentPointsBuffer = mutableListOf<LoungePoint>()
    private var batchJob: Job? = null
    
    val isDrawingUploading = MutableStateFlow(false)

    init {
        startStrokeBatchingScheduler()
        viewModelScope.launch {
            loungeSyncUseCase.observeEvents().collect { event ->
                when (event) {
                    is LoungeIncomingEvent.CanvasEvent -> {
                        if (event.event.action == "CLEAR") {
                            localStrokes.clear()
                            partnerStrokes.clear()
                        } else if (event.event.action == "START") {
                            _currentPartnerStroke.value = LoungeStroke(event.event.points, event.event.colorHex, event.event.brushWidth)
                        } else if (event.event.action == "MOVE") {
                            val active = _currentPartnerStroke.value
                            if (active != null) {
                                _currentPartnerStroke.value = active.copy(points = active.points + event.event.points)
                            }
                        } else if (event.event.action == "END") {
                            val active = _currentPartnerStroke.value
                            if (active != null) {
                                val finalStroke = active.copy(points = active.points + event.event.points)
                                partnerStrokes.add(finalStroke)
                                _currentPartnerStroke.value = null
                            }
                        }
                    }
                    is LoungeIncomingEvent.CanvasClear -> {
                        localStrokes.clear()
                        partnerStrokes.clear()
                    }
                    else -> {}
                }
            }
        }
    }

    fun sendLoungeMessage(type: String, payload: String) {
        // Use plain sendRawMessage (same as all other lounge VMs) so LoungeSyncUseCase
        // can decode it from incomingRawMessages. Canvas strokes are non-sensitive
        // meta-data transmitted over TLS — Signal double-encryption is unnecessary here
        // and was silently dropping all canvas events on the receiving end.
        viewModelScope.launch {
            val wrapper = SignalMessageWrapper(
                type = type,
                senderId = myId,
                targetId = partnerId,
                payload = payload
            )
            signalingClient.sendRawMessage(Json.encodeToString(wrapper))
        }
    }

    fun startLocalStroke(x: Float, y: Float, colorHex: String, brushWidth: Float) {
        val startPoint = LoungePoint(x, y)
        _currentLocalStroke.value = LoungeStroke(listOf(startPoint), colorHex, brushWidth)
        synchronized(currentPointsBuffer) {
            currentPointsBuffer.clear()
            currentPointsBuffer.add(startPoint)
        }
        val event = LoungeDrawEvent("START", listOf(startPoint), colorHex, brushWidth)
        sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
    }

    fun addLocalStrokePoint(x: Float, y: Float) {
        val nextPoint = LoungePoint(x, y)
        val active = _currentLocalStroke.value
        if (active != null) {
            _currentLocalStroke.value = active.copy(points = active.points + nextPoint)
        }
        synchronized(currentPointsBuffer) {
            currentPointsBuffer.add(nextPoint)
        }
    }

    fun finalizeLocalStroke() {
        val active = _currentLocalStroke.value
        if (active != null) {
            localStrokes.add(active)
            _currentLocalStroke.value = null
        }
        val remaining = synchronized(currentPointsBuffer) {
            val list = currentPointsBuffer.toList()
            currentPointsBuffer.clear()
            list
        }
        val event = LoungeDrawEvent("END", remaining, active?.colorHex ?: "#E598A7", active?.brushWidth ?: 8f)
        sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
    }

    fun clearCanvas() {
        localStrokes.clear()
        partnerStrokes.clear()
        _currentLocalStroke.value = null
        _currentPartnerStroke.value = null
        sendLoungeMessage("LOUNGE_CANVAS_CLEAR", "")
    }

    private fun startStrokeBatchingScheduler() {
        batchJob = viewModelScope.launch {
            while (isActive) {
                delay(50L) // 50ms batching for smooth and efficient remote sync
                flushStrokeBatch()
            }
        }
    }

    private fun flushStrokeBatch() {
        val pointsToFlush = synchronized(currentPointsBuffer) {
            if (currentPointsBuffer.size > 1) {
                val list = currentPointsBuffer.toList()
                val lastPoint = currentPointsBuffer.last()
                currentPointsBuffer.clear()
                currentPointsBuffer.add(lastPoint)
                list
            } else null
        }

        if (pointsToFlush != null) {
            val active = _currentLocalStroke.value
            val event = LoungeDrawEvent(
                action = "MOVE",
                points = pointsToFlush,
                colorHex = active?.colorHex ?: "#E598A7",
                brushWidth = active?.brushWidth ?: 8f
            )
            viewModelScope.launch {
                sendLoungeMessage("LOUNGE_CANVAS_EVENT", Json.encodeToString(event))
            }
        }
    }

    fun saveCanvasToGallery(title: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isDrawingUploading.value = true
            try {
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

                val allStrokes = localStrokes.toList() + partnerStrokes.toList()
                allStrokes.forEach { drawStrokeToBitmap(it) }

                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val bytes = outputStream.toByteArray()

                val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val url = repo.uploadDrawingFile("${safeTitle}_${System.currentTimeMillis()}.png", bytes)
                repo.insertLoungeDrawing(title, url)
                sendLoungeMessage("LOUNGE_DRAWINGS_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeDrawingViewModel", "Failed to save drawing", e)
            } finally {
                isDrawingUploading.value = false
            }
        }
    }

    class Factory(
        private val signalingClient: SignalingClient,
        private val bundleRepository: BundleRepository?,
        private val partnerId: String,
        private val myId: String,
        private val loungeSyncUseCase: LoungeSyncUseCase
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoungeDrawingViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoungeDrawingViewModel(signalingClient, bundleRepository, partnerId, myId, loungeSyncUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
