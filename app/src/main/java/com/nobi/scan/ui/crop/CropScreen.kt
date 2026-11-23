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

import com.nobi.scan.ui.components.rememberNobiHaptics
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nobi.scan.ui.theme.LocalNobiPalette

@Composable
internal fun CropEdgesViewport(bitmap: Bitmap, state: CropScreenState, modifier: Modifier = Modifier) {
    val haptics = rememberNobiHaptics()
    val palette = LocalNobiPalette.current
    val quadHandler = remember { QuadEditingHandler() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    val density = LocalDensity.current
    val liftWiggleThresholdPx = with(density) {
        CropScreenState.LIFT_WIGGLE_MAX_DISTANCE.toPx()
    }

    Box(
        modifier = modifier.fillMaxSize().padding(12.dp).onGloballyPositioned { coordinates ->
            state.containerSize = coordinates.size
            state.displaySize = QuadCoordinateUtils.calculateDisplaySize(
                bitmap.width, bitmap.height, coordinates.size
            )
        },
        contentAlignment = Alignment.Center
    ) {
        if (state.containerSize.width > 0 && state.containerSize.height > 0) {
            // 1. Scaled Document Background Image
            Image(
                bitmap = imageBitmap,
                contentDescription = "Document to crop",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // 2. High-Performance Drag Overlay with DUAL pointerInput(Unit)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // PointerInput 1: Continuous drag tracking (NEVER cancelled during dragging)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startPos ->
                                state.dragPosition = startPos
                                val cornerIdx = if (state.touchDownCornerIndex >= 0) {
                                    state.touchDownCornerIndex
                                } else {
                                    quadHandler.findTouchedCorner(
                                        startPos, state.currentQuad, state.containerSize, state.displaySize
                                    )
                                }
                                if (cornerIdx >= 0) {
                                    haptics.grab()
                                    state.startCornerDrag(cornerIdx)
                                }
                            },
                            onDragEnd = {
                                state.rollbackLastDragStepIfLikelyLiftWiggle(liftWiggleThresholdPx)
                                state.endDrag()
                            },
                            onDragCancel = {
                                state.rollbackLastDragStepIfLikelyLiftWiggle(liftWiggleThresholdPx)
                                state.endDrag()
                            },
                            onDrag = { change, dragAmount ->
                                state.dragPosition = change.position
                                if (state.draggedCornerIndex >= 0) {
                                    state.recordDragStep(state.currentQuad, dragAmount)
                                    val normalizedDelta = QuadCoordinateUtils.screenDeltaToNormalized(
                                        dragAmount, state.displaySize
                                    )
                                    state.currentQuad = quadHandler.updateQuadCorner(
                                        state.currentQuad, state.draggedCornerIndex, normalizedDelta
                                    )
                                }
                            }
                        )
                    }
                    // PointerInput 2: Instantaneous touch-down response before touch-slop
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cIdx = quadHandler.findTouchedCorner(
                                down.position, state.currentQuad, state.containerSize, state.displaySize
                            )
                            if (cIdx >= 0) {
                                state.onTouchDown(down.position, cIdx)
                            }
                            if (waitForUpOrCancellation() != null) {
                                state.onTouchUp()
                            }
                        }
                    }
            ) {
                val pTl = QuadCoordinateUtils.normalizedToScreen(state.currentQuad.topLeft, state.containerSize, state.displaySize)
                val pTr = QuadCoordinateUtils.normalizedToScreen(state.currentQuad.topRight, state.containerSize, state.displaySize)
                val pBr = QuadCoordinateUtils.normalizedToScreen(state.currentQuad.bottomRight, state.containerSize, state.displaySize)
                val pBl = QuadCoordinateUtils.normalizedToScreen(state.currentQuad.bottomLeft, state.containerSize, state.displaySize)

                val cropPath = Path().apply {
                    moveTo(pTl.x, pTl.y)
                    lineTo(pTr.x, pTr.y)
                    lineTo(pBr.x, pBr.y)
                    lineTo(pBl.x, pBl.y)
                    close()
                }

                // Translucent document tint
                drawPath(path = cropPath, color = palette.accent.copy(alpha = .08f))

                // Rule of thirds lines
                for (i in 1..2) {
                    val f = i / 3f
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(pTl.x + (pTr.x - pTl.x) * f, pTl.y + (pTr.y - pTl.y) * f),
                        end = Offset(pBl.x + (pBr.x - pBl.x) * f, pBl.y + (pBr.y - pBl.y) * f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(pTl.x + (pBl.x - pTl.x) * f, pTl.y + (pBl.y - pTl.y) * f),
                        end = Offset(pTr.x + (pBr.x - pTr.x) * f, pTr.y + (pBr.y - pTr.y) * f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Border stroke
                drawPath(
                    path = cropPath,
                    color = palette.accent,
                    style = Stroke(width = 2.8.dp.toPx())
                )

                // 4 Corner Touch Handles
                val activeIndex = if (state.draggedCornerIndex >= 0) {
                    state.draggedCornerIndex
                } else {
                    state.touchDownCornerIndex
                }

                listOf(pTl, pTr, pBr, pBl).forEachIndexed { idx, point ->
                    val isActive = idx == activeIndex
                    drawCircle(
                        color = palette.accent,
                        radius = (if (isActive) 14.dp else 9.dp).toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = (if (isActive) 8.dp else 5.dp).toPx(),
                        center = point
                    )
                }
            }

            // 3. Decoupled Zero-Lag Magnifying Glass
            val activeCorner = if (state.draggedCornerIndex >= 0) {
                state.draggedCornerIndex
            } else if (state.touchDownCornerIndex >= 0) {
                state.touchDownCornerIndex
            } else {
                -1
            }

            if (state.isTouching && activeCorner >= 0) {
                val focusPoint = when (activeCorner) {
                    0 -> QuadCoordinateUtils.normalizedToScreen(state.currentQuad.topLeft, state.containerSize, state.displaySize)
                    1 -> QuadCoordinateUtils.normalizedToScreen(state.currentQuad.topRight, state.containerSize, state.displaySize)
                    2 -> QuadCoordinateUtils.normalizedToScreen(state.currentQuad.bottomRight, state.containerSize, state.displaySize)
                    3 -> QuadCoordinateUtils.normalizedToScreen(state.currentQuad.bottomLeft, state.containerSize, state.displaySize)
                    else -> state.dragPosition
                }

                MagnifyingGlass(
                    bitmap = bitmap,
                    imageBitmap = imageBitmap,
                    fingerPosition = state.dragPosition,
                    focusPosition = focusPoint,
                    containerSize = state.containerSize,
                    displaySize = state.displaySize,
                    quad = state.currentQuad
                )
            }
        }
    }

}
