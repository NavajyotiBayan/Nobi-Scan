package com.nobi.scan.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.PageTrim
import com.nobi.scan.data.TrimRegion
import com.nobi.scan.data.straightenScale
import com.nobi.scan.ui.components.rememberNobiHaptics
import kotlin.math.roundToInt

@Composable
internal fun TrimViewport(bitmap: Bitmap, trim: PageTrim, accent: Color, onRegion: (TrimRegion) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val haptics = rememberNobiHaptics()
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val edit by rememberUpdatedState(trim)
    val update by rememberUpdatedState(onRegion)

    Canvas(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Crop preview. Drag a corner to trim. Pinch to zoom." }
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val initial = imageRect(viewport, bitmap, zoom, pan)
                    val corners = cropRect(initial, edit.region).corners()
                    val handle = corners.indices.minByOrNull { (corners[it] - down.position).getDistance() }
                        ?.takeIf { (corners[it] - down.position).getDistance() < 36.dp.toPx() }
                    var pinching = false
                    var grabbed = false
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2) {
                            if (!pinching) haptics.grab()
                            pinching = true
                            val next = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            val center = Offset(viewport.width / 2, viewport.height / 2)
                            val focus = event.calculateCentroid(useCurrent = false).takeIf { it != Offset.Unspecified }
                                ?: center
                            val moved = (pan + center - focus) * (next / zoom) + focus - center + event.calculatePan()
                            val base = imageRect(viewport, bitmap, 1f, Offset.Zero)
                            val limitX = (base.width * (next - 1)) / 2
                            val limitY = (base.height * (next - 1)) / 2
                            pan = Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
                            zoom = next
                            event.changes.forEach { it.consume() }
                        } else if (handle != null && !pinching) {
                            val pointer = event.changes.firstOrNull { it.id == down.id && it.pressed }
                            if (pointer != null && pointer.position != down.position) {
                                if (!grabbed) {
                                    haptics.grab()
                                    grabbed = true
                                }
                                val position = corners[handle] + pointer.position - down.position
                                update(edit.region.dragCorner(
                                    handle, (position.x - initial.left) / initial.width,
                                    (position.y - initial.top) / initial.height,
                                    edit.ratio.normalized(bitmap.width, bitmap.height)
                                ))
                                pointer.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val fitted = imageRect(size, bitmap, zoom, pan)
        val crop = cropRect(fitted, trim.region)
        val coverage = straightenScale(fitted.width, fitted.height, trim.degrees)
        clipRect(fitted.left, fitted.top, fitted.right, fitted.bottom) {
            scale(coverage, pivot = fitted.center) {
                rotate(trim.degrees, pivot = fitted.center) {
                    drawImage(
                        image, dstOffset = IntOffset(fitted.left.roundToInt(), fitted.top.roundToInt()),
                        dstSize = IntSize(fitted.width.roundToInt(), fitted.height.roundToInt())
                    )
                }
            }
            val scrim = Color.Black.copy(alpha = .58f)
            drawRect(scrim, fitted.topLeft, Size(fitted.width, crop.top - fitted.top))
            drawRect(scrim, Offset(fitted.left, crop.bottom), Size(fitted.width, fitted.bottom - crop.bottom))
            drawRect(scrim, Offset(fitted.left, crop.top), Size(crop.left - fitted.left, crop.height))
            drawRect(scrim, Offset(crop.right, crop.top), Size(fitted.right - crop.right, crop.height))
        }
        for (part in 1..2) {
            val x = crop.left + crop.width * part / 3
            val y = crop.top + crop.height * part / 3
            drawLine(Color.White.copy(alpha = .55f), Offset(x, crop.top), Offset(x, crop.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = .55f), Offset(crop.left, y), Offset(crop.right, y), 1.dp.toPx())
        }
        drawRect(accent, crop.topLeft, crop.size, style = Stroke(2.dp.toPx()))
        val handleLength = minOf(14.dp.toPx(), crop.width / 3, crop.height / 3)
        crop.corners().forEachIndexed { index, corner ->
            val horizontal = Offset(if (index == 0 || index == 3) handleLength else -handleLength, 0f)
            val vertical = Offset(0f, if (index < 2) handleLength else -handleLength)
            drawLine(Color.White, corner, corner + horizontal, 4.dp.toPx())
            drawLine(Color.White, corner, corner + vertical, 4.dp.toPx())
        }
    }
}

private fun imageRect(view: Size, bitmap: Bitmap, zoom: Float, pan: Offset): Rect {
    val fit = minOf((view.width - 24f) / bitmap.width, (view.height - 24f) / bitmap.height).coerceAtLeast(.001f)
    val width = bitmap.width * fit * zoom
    val height = bitmap.height * fit * zoom
    val x = (view.width - width) / 2 + pan.x
    val y = (view.height - height) / 2 + pan.y
    return Rect(x, y, x + width, y + height)
}

private fun cropRect(image: Rect, region: TrimRegion): Rect = Rect(
    image.left + image.width * region.left, image.top + image.height * region.top,
    image.left + image.width * region.right, image.top + image.height * region.bottom
)

private fun Rect.corners() = listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom))
