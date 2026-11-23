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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nobi.scan.ui.camera.components.DocumentQuadData

class CropScreenState(initialQuad: DocumentQuadData) {

    companion object {
        val LIFT_WIGGLE_MAX_DISTANCE = 8.dp
        const val LIFT_WIGGLE_WINDOW_MS = 80L
    }

    var currentQuad by mutableStateOf(initialQuad)
    var containerSize by mutableStateOf(IntSize.Zero)
    var displaySize by mutableStateOf(IntSize.Zero)

    var draggedCornerIndex by mutableIntStateOf(-1)
    var touchDownCornerIndex by mutableIntStateOf(-1)
    var dragPosition by mutableStateOf(Offset.Zero)
    var isTouching by mutableStateOf(false)

    private var quadBeforeLastDragStep: DocumentQuadData? = null
    private var lastDragStepDistancePx: Float = Float.MAX_VALUE
    private var lastDragStepAtMs: Long = 0L

    fun onTouchDown(position: Offset, cornerIndex: Int) {
        touchDownCornerIndex = cornerIndex
        dragPosition = position
        isTouching = true
    }

    fun onTouchUp() {
        isTouching = false
        touchDownCornerIndex = -1
    }

    fun startCornerDrag(cornerIndex: Int) {
        draggedCornerIndex = cornerIndex
        isTouching = true
        quadBeforeLastDragStep = null
    }

    fun recordDragStep(quad: DocumentQuadData, dragAmount: Offset) {
        quadBeforeLastDragStep = quad
        lastDragStepDistancePx = dragAmount.getDistance()
        lastDragStepAtMs = System.currentTimeMillis()
    }

    fun rollbackLastDragStepIfLikelyLiftWiggle(
        maxDistancePx: Float,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val prevQuad = quadBeforeLastDragStep ?: return
        val isRecent = nowMs - lastDragStepAtMs <= LIFT_WIGGLE_WINDOW_MS
        val isSmall = lastDragStepDistancePx <= maxDistancePx
        if (isRecent && isSmall) {
            currentQuad = prevQuad
        }
    }

    fun endDrag() {
        quadBeforeLastDragStep = null
        draggedCornerIndex = -1
        touchDownCornerIndex = -1
        isTouching = false
    }

    fun reset(initialQuad: DocumentQuadData) {
        currentQuad = initialQuad
        endDrag()
    }
}
