package com.nobi.scan.cv

import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint
import kotlin.math.hypot

class QuadStabilizer(
    private val maxHistory: Int = 4,
    private val jitterThreshold: Float = 0.015f
) {
    private val history = mutableListOf<DocumentQuadData>()

    fun update(detected: DocumentQuadData): DocumentQuadData {
        if (detected.confidence < 0.40f) {
            history.clear()
            return detected
        }

        if (history.isEmpty()) {
            history.add(detected)
            return detected
        }

        val last = history.last()
        val dTl = hypot(detected.topLeft.x - last.topLeft.x, detected.topLeft.y - last.topLeft.y)
        val dTr = hypot(detected.topRight.x - last.topRight.x, detected.topRight.y - last.topRight.y)
        val dBr = hypot(detected.bottomRight.x - last.bottomRight.x, detected.bottomRight.y - last.bottomRight.y)
        val dBl = hypot(detected.bottomLeft.x - last.bottomLeft.x, detected.bottomLeft.y - last.bottomLeft.y)
        val avgDelta = (dTl + dTr + dBr + dBl) / 4f

        // If paper moved significantly, reset history and track immediately
        if (avgDelta > 0.12f) {
            history.clear()
            history.add(detected)
            return detected
        }

        history.add(detected)
        if (history.size > maxHistory) {
            history.removeAt(0)
        }

        // Compute moving average of corners
        var tlX = 0f; var tlY = 0f
        var trX = 0f; var trY = 0f
        var brX = 0f; var brY = 0f
        var blX = 0f; var blY = 0f
        var conf = 0f

        for (q in history) {
            tlX += q.topLeft.x; tlY += q.topLeft.y
            trX += q.topRight.x; trY += q.topRight.y
            brX += q.bottomRight.x; brY += q.bottomRight.y
            blX += q.bottomLeft.x; blY += q.bottomLeft.y
            conf += q.confidence
        }

        val count = history.size.toFloat()
        return DocumentQuadData(
            topLeft = QuadPoint(tlX / count, tlY / count),
            topRight = QuadPoint(trX / count, trY / count),
            bottomRight = QuadPoint(brX / count, brY / count),
            bottomLeft = QuadPoint(blX / count, blY / count),
            confidence = (conf / count).coerceIn(0f, 1f)
        )
    }

    fun reset() {
        history.clear()
    }
}
