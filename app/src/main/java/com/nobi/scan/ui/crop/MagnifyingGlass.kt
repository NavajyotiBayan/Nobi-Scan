/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
// Adapted for Nobi Scan from the FairScan crop editor.
package com.nobi.scan.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.theme.AccentCyan
import kotlin.math.roundToInt

@Composable
fun MagnifyingGlass(
    bitmap: Bitmap,
    imageBitmap: ImageBitmap,
    fingerPosition: Offset,
    focusPosition: Offset,
    containerSize: IntSize,
    displaySize: IntSize,
    quad: DocumentQuadData?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val loupeRadiusDp = 58.dp
    val loupeRadiusPx = with(density) { loupeRadiusDp.toPx() }
    val loupeDiameterPx = loupeRadiusPx * 2f
    val verticalOffsetPx = with(density) { 45.dp.toPx() }
    val screenMarginPx = with(density) { 10.dp.toPx() }
    val borderWidthPx = with(density) { 3.dp.toPx() }

    // Compute Loupe Center (Above finger, or left/right if near top screen border)
    val loupeCenter = remember(fingerPosition, containerSize) {
        val aboveY = fingerPosition.y - verticalOffsetPx - loupeRadiusPx
        if (aboveY - loupeRadiusPx >= screenMarginPx) {
            val cx = fingerPosition.x.coerceIn(
                screenMarginPx + loupeRadiusPx,
                containerSize.width.toFloat() - screenMarginPx - loupeRadiusPx
            )
            Offset(cx, aboveY)
        } else {
            val leftX = fingerPosition.x - verticalOffsetPx - loupeRadiusPx
            if (leftX - loupeRadiusPx >= screenMarginPx) {
                Offset(leftX, fingerPosition.y.coerceAtLeast(screenMarginPx + loupeRadiusPx))
            } else {
                val rightX = (fingerPosition.x + verticalOffsetPx + loupeRadiusPx).coerceAtMost(
                    containerSize.width.toFloat() - screenMarginPx - loupeRadiusPx
                )
                Offset(rightX, fingerPosition.y.coerceAtLeast(screenMarginPx + loupeRadiusPx))
            }
        }
    }

    val dispW = if (displaySize.width > 0) displaySize.width.toFloat() else 1f
    val dispH = if (displaySize.height > 0) displaySize.height.toFloat() else 1f

    val imageOffset = QuadCoordinateUtils.getImageOffset(containerSize, displaySize)
    val bitmapX = (((focusPosition.x - imageOffset.width) / dispW) * bitmap.width)
        .coerceIn(0f, (bitmap.width - 1).toFloat())
    val bitmapY = (((focusPosition.y - imageOffset.height) / dispH) * bitmap.height)
        .coerceIn(0f, (bitmap.height - 1).toFloat())

    // Zoom factor: 2.8x
    val zoomFactor = 2.8f
    val bitmapRegionHalf = (loupeDiameterPx / zoomFactor) / 2f

    Canvas(
        modifier = modifier
            .size(loupeRadiusDp * 2)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val placeX = (loupeCenter.x - loupeRadiusPx).roundToInt()
                    val placeY = (loupeCenter.y - loupeRadiusPx).roundToInt()
                    placeable.place(placeX, placeY)
                }
            }
    ) {
        val circlePath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(center = Offset(loupeRadiusPx, loupeRadiusPx), radius = loupeRadiusPx))
        }

        clipPath(circlePath) {
            drawRect(color = Color.Black)

            val srcLeft = (bitmapX - bitmapRegionHalf).toInt().coerceAtLeast(0)
            val srcTop = (bitmapY - bitmapRegionHalf).toInt().coerceAtLeast(0)
            val srcRight = (bitmapX + bitmapRegionHalf).toInt().coerceAtMost(bitmap.width)
            val srcBottom = (bitmapY + bitmapRegionHalf).toInt().coerceAtMost(bitmap.height)

            if (srcRight > srcLeft && srcBottom > srcTop) {
                val dstOffsetX = ((srcLeft - (bitmapX - bitmapRegionHalf)) / (2 * bitmapRegionHalf) * loupeDiameterPx)
                val dstOffsetY = ((srcTop - (bitmapY - bitmapRegionHalf)) / (2 * bitmapRegionHalf) * loupeDiameterPx)
                val dstWidth = ((srcRight - srcLeft) / (2 * bitmapRegionHalf) * loupeDiameterPx).toInt()
                val dstHeight = ((srcBottom - srcTop) / (2 * bitmapRegionHalf) * loupeDiameterPx).toInt()

                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
                    dstOffset = IntOffset(dstOffsetX.toInt(), dstOffsetY.toInt()),
                    dstSize = IntSize(dstWidth, dstHeight)
                )
            }

            // Draw quad outline inside loupe
            if (quad != null) {
                val bitmapOriginX = bitmapX - bitmapRegionHalf
                val bitmapOriginY = bitmapY - bitmapRegionHalf
                val scale = loupeDiameterPx / (2 * bitmapRegionHalf)

                fun toLoupe(nx: Float, ny: Float): Offset {
                    val bx = nx * bitmap.width
                    val by = ny * bitmap.height
                    return Offset((bx - bitmapOriginX) * scale, (by - bitmapOriginY) * scale)
                }

                val pts = listOf(
                    toLoupe(quad.topLeft.x, quad.topLeft.y),
                    toLoupe(quad.topRight.x, quad.topRight.y),
                    toLoupe(quad.bottomRight.x, quad.bottomRight.y),
                    toLoupe(quad.bottomLeft.x, quad.bottomLeft.y)
                )

                for (i in 0 until 4) {
                    drawLine(
                        color = AccentCyan.copy(alpha = 0.8f),
                        start = pts[i],
                        end = pts[(i + 1) % 4],
                        strokeWidth = 2.5f
                    )
                }
            }

            // Red Target Crosshair
            val c = Offset(loupeRadiusPx, loupeRadiusPx)
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(0f, c.y),
                end = Offset(loupeDiameterPx, c.y),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(c.x, 0f),
                end = Offset(c.x, loupeDiameterPx),
                strokeWidth = 2f
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = c
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = 2.5f,
                center = c
            )
        }

        // Frosted Rim Border
        drawCircle(
            color = Color.White,
            radius = loupeRadiusPx - borderWidthPx / 2,
            center = Offset(loupeRadiusPx, loupeRadiusPx),
            style = Stroke(width = borderWidthPx)
        )
    }
}
