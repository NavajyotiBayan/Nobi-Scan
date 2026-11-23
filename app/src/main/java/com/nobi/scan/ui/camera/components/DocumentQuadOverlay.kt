package com.nobi.scan.ui.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class QuadPoint(val x: Float, val y: Float)

data class DocumentQuadData(
    val topLeft: QuadPoint,
    val topRight: QuadPoint,
    val bottomRight: QuadPoint,
    val bottomLeft: QuadPoint,
    val confidence: Float
) {
    companion object {
        fun fullPage() = DocumentQuadData(
            topLeft = QuadPoint(0f, 0f),
            topRight = QuadPoint(1f, 0f),
            bottomRight = QuadPoint(1f, 1f),
            bottomLeft = QuadPoint(0f, 1f),
            confidence = 0f
        )

        fun defaultGuide(): DocumentQuadData = DocumentQuadData(
            topLeft = QuadPoint(0.12f, 0.18f),
            topRight = QuadPoint(0.88f, 0.18f),
            bottomRight = QuadPoint(0.88f, 0.82f),
            bottomLeft = QuadPoint(0.12f, 0.82f),
            confidence = 0.0f
        )
    }
}

@Composable
fun DocumentQuadOverlay(
    quadData: DocumentQuadData,
    cameraWidth: Int = 480,
    cameraHeight: Int = 640,
    modifier: Modifier = Modifier
) {
    // Smooth spring interpolation for the 4 corner points
    val animTlX by animateFloatAsState(targetValue = quadData.topLeft.x, animationSpec = spring(stiffness = 300f), label = "tl_x")
    val animTlY by animateFloatAsState(targetValue = quadData.topLeft.y, animationSpec = spring(stiffness = 300f), label = "tl_y")
    val animTrX by animateFloatAsState(targetValue = quadData.topRight.x, animationSpec = spring(stiffness = 300f), label = "tr_x")
    val animTrY by animateFloatAsState(targetValue = quadData.topRight.y, animationSpec = spring(stiffness = 300f), label = "tr_y")
    val animBrX by animateFloatAsState(targetValue = quadData.bottomRight.x, animationSpec = spring(stiffness = 300f), label = "br_x")
    val animBrY by animateFloatAsState(targetValue = quadData.bottomRight.y, animationSpec = spring(stiffness = 300f), label = "br_y")
    val animBlX by animateFloatAsState(targetValue = quadData.bottomLeft.x, animationSpec = spring(stiffness = 300f), label = "bl_x")
    val animBlY by animateFloatAsState(targetValue = quadData.bottomLeft.y, animationSpec = spring(stiffness = 300f), label = "bl_y")

    val isLocked = quadData.confidence >= 0.65f
    val meshColor = androidx.compose.ui.graphics.Color(0xFFC6ADFF).copy(alpha = if (isLocked) 1f else .50f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height

        val srcW = cameraWidth.toFloat().coerceAtLeast(1f)
        val srcH = cameraHeight.toFloat().coerceAtLeast(1f)

        // Compute aspect-fill transform matching PreviewView.ScaleType.FILL_CENTER
        val scale = maxOf(viewW / srcW, viewH / srcH)
        val renderedW = srcW * scale
        val renderedH = srcH * scale
        val offsetX = (viewW - renderedW) / 2f
        val offsetY = (viewH - renderedH) / 2f

        fun mapToScreen(normX: Float, normY: Float): Offset = Offset(
            normX * renderedW + offsetX,
            normY * renderedH + offsetY
        )

        val pTl = mapToScreen(animTlX, animTlY)
        val pTr = mapToScreen(animTrX, animTrY)
        val pBr = mapToScreen(animBrX, animBrY)
        val pBl = mapToScreen(animBlX, animBlY)

        val path = Path().apply {
            moveTo(pTl.x, pTl.y)
            lineTo(pTr.x, pTr.y)
            lineTo(pBr.x, pBr.y)
            lineTo(pBl.x, pBl.y)
            close()
        }

        drawPath(
            path = path,
            color = meshColor,
            style = Stroke(width = if (isLocked) 1.6.dp.toPx() else 1.dp.toPx())
        )

        val corners = listOf(pTl, pTr, pBr, pBl)
        val bracketLength = 15.dp.toPx()
        corners.forEachIndexed { index, corner ->
            val neighbours = listOf(corners[(index + 1) % 4], corners[(index + 3) % 4])
            for (neighbour in neighbours) {
                val edge = neighbour - corner
                val fraction = (bracketLength / edge.getDistance().coerceAtLeast(1f)).coerceAtMost(.3f)
                drawLine(
                    color = meshColor,
                    start = corner,
                    end = corner + edge * fraction,
                    strokeWidth = 2.8.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
