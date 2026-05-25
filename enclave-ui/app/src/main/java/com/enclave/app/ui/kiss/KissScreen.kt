package com.enclave.app.ui.kiss

import androidx.compose.runtime.Composable
import com.enclave.app.ui.chat.components.KissGestureCanvasOverlay

@Composable
fun KissScreen(
    viewModel: com.enclave.app.ui.kiss.KissViewModel,
    signalingClient: com.enclave.app.webrtc.SignalingClient,
    onClose: () -> Unit
) {
    KissGestureCanvasOverlay(viewModel = viewModel, signalingClient = signalingClient, onClose = onClose)
}
