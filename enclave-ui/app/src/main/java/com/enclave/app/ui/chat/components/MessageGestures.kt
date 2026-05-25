package com.enclave.app.ui.chat.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.swipeToReplyGesture(
    offsetX: Float,
    onOffsetChange: (Float) -> Unit,
    onReplyTriggered: () -> Unit,
    haptic: HapticFeedback
): Modifier = composed {
    draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            val newOffset = offsetX + delta
            if (newOffset >= 0f && newOffset < 150f) {
                onOffsetChange(newOffset)
            }
        },
        onDragStopped = {
            if (offsetX > 80f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReplyTriggered()
            }
            onOffsetChange(0f)
        }
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
