package com.nobi.scan.ui.tools

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobi.scan.ui.components.rememberNobiHaptics
import kotlin.math.abs
import kotlin.math.sign

@Composable
internal fun PdfReorderHandle(
    label: String,
    index: Int,
    lastIndex: Int,
    enabled: Boolean,
    rowStride: Dp,
    onDragActive: (Boolean) -> Unit,
    onMove: (Int) -> Boolean
) {
    val haptics = rememberNobiHaptics()
    val move by rememberUpdatedState(onMove)
    val setActive by rememberUpdatedState(onDragActive)
    Box(
        Modifier.size(48.dp).semantics {
            contentDescription = label
            customActions = buildList {
                if (enabled && index > 0) add(CustomAccessibilityAction("Move up") { move(-1) })
                if (enabled && index < lastIndex) add(CustomAccessibilityAction("Move down") { move(1) })
            }
        }.pointerInput(enabled, rowStride) {
            if (!enabled) return@pointerInput
            val stride = rowStride.toPx()
            var distance = 0f
            detectDragGestures(
                onDragStart = {
                    distance = 0f
                    setActive(true)
                    haptics.grab()
                },
                onDragEnd = { setActive(false) },
                onDragCancel = { setActive(false) }
            ) { change, delta ->
                change.consume()
                distance += delta.y
                while (abs(distance) > stride / 2) {
                    val direction = distance.sign.toInt()
                    if (!move(direction)) {
                        distance = 0f
                        break
                    }
                    distance -= stride * direction
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DragHandle, contentDescription = null)
    }
}
