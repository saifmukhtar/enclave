package dev.saifmukhtar.enclave.ui.kiss

import androidx.compose.runtime.Composable
import dev.saifmukhtar.enclave.ui.chat.components.KissGestureCanvasOverlay

@Composable
fun KissScreen(
    viewModel: dev.saifmukhtar.enclave.ui.kiss.KissViewModel,
    signalingClient: dev.saifmukhtar.enclave.webrtc.SignalingClient,
    onClose: () -> Unit
) {
    KissGestureCanvasOverlay(viewModel = viewModel, signalingClient = signalingClient, onClose = onClose)
}
