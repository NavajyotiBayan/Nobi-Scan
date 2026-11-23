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
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint

class QuadEditingHandler {

    companion object {
        const val CORNER_TOUCH_RADIUS = 95f
    }

    fun findTouchedCorner(
        touchPos: Offset,
        quad: DocumentQuadData,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        val corners = listOf(
            QuadCoordinateUtils.normalizedToScreen(quad.topLeft, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.topRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomLeft, containerSize, displaySize)
        )

        return corners
            .mapIndexed { index, corner -> index to (touchPos - corner).getDistance() }
            .filter { (_, distance) -> distance < CORNER_TOUCH_RADIUS }
            .minByOrNull { (_, distance) -> distance }
            ?.first ?: -1
    }

    fun updateQuadCorner(
        quad: DocumentQuadData,
        cornerIndex: Int,
        normalizedDelta: Offset
    ): DocumentQuadData {
        fun clamp(p: QuadPoint): QuadPoint = QuadPoint(
            x = (p.x + normalizedDelta.x).coerceIn(0f, 1f),
            y = (p.y + normalizedDelta.y).coerceIn(0f, 1f)
        )

        val candidate = when (cornerIndex) {
            0 -> quad.copy(topLeft = clamp(quad.topLeft))
            1 -> quad.copy(topRight = clamp(quad.topRight))
            2 -> quad.copy(bottomRight = clamp(quad.bottomRight))
            3 -> quad.copy(bottomLeft = clamp(quad.bottomLeft))
            else -> quad
        }

        return if (isQuadConvex(candidate)) candidate else quad
    }

    private fun isQuadConvex(q: DocumentQuadData): Boolean {
        val pts = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
        var sign = 0
        for (i in 0 until 4) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]

            val cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x)
            val currentSign = if (cross > 0f) 1 else if (cross < 0f) -1 else 0

            if (currentSign != 0) {
                if (sign == 0) {
                    sign = currentSign
                } else if (sign != currentSign) {
                    return false
                }
            }
        }
        return true
    }
}
