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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.nobi.scan.ui.camera.components.QuadPoint

object QuadCoordinateUtils {

    fun calculateDisplaySize(
        bitmapWidth: Int,
        bitmapHeight: Int,
        containerSize: IntSize
    ): IntSize {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerSize.width <= 0 || containerSize.height <= 0) {
            return containerSize
        }
        val imageAspectRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()

        return if (imageAspectRatio > containerAspectRatio) {
            IntSize(containerSize.width, (containerSize.width / imageAspectRatio).toInt())
        } else {
            IntSize((containerSize.height * imageAspectRatio).toInt(), containerSize.height)
        }
    }

    fun normalizedToScreen(point: QuadPoint, containerSize: IntSize, displaySize: IntSize): Offset {
        val offsetX = (containerSize.width - displaySize.width) / 2f
        val offsetY = (containerSize.height - displaySize.height) / 2f
        return Offset(
            x = (point.x * displaySize.width) + offsetX,
            y = (point.y * displaySize.height) + offsetY
        )
    }

    fun screenDeltaToNormalized(delta: Offset, displaySize: IntSize): Offset {
        val w = if (displaySize.width > 0) displaySize.width.toFloat() else 1f
        val h = if (displaySize.height > 0) displaySize.height.toFloat() else 1f
        return Offset(
            x = delta.x / w,
            y = delta.y / h
        )
    }

    fun getImageOffset(containerSize: IntSize, displaySize: IntSize): IntSize {
        return IntSize(
            (containerSize.width - displaySize.width) / 2,
            (containerSize.height - displaySize.height) / 2
        )
    }
}
