package dev.saifmukhtar.enclave.ui.chat.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset

fun Modifier.swipeToReplyGesture(
    onReplyTriggered: () -> Unit,
    haptic: HapticFeedback
): Modifier = composed {
    var offsetX by remember { mutableStateOf(0f) }
    
    this.then(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val newOffset = offsetX + delta
                    if (newOffset >= 0f && newOffset < 150f) {
                        offsetX = newOffset
                    }
                },
                onDragStopped = {
                    if (offsetX > 80f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReplyTriggered()
                    }
                    offsetX = 0f
                }
            )
    )
}

fun Modifier.messageContextMenuGesture(
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    haptic: HapticFeedback
): Modifier = composed {
    pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            },
            onTap = { onTap() }
        )
    }
}
