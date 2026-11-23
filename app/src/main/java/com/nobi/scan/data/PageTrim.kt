package com.nobi.scan.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class TrimRegion(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun rotateRight(): TrimRegion = TrimRegion(1f - bottom, left, 1f - top, right)

    internal fun fitRatio(ratio: Float): TrimRegion {
        val w = minOf(width, height * ratio)
        val h = w / ratio
        val x = (left + right - w) / 2
        val y = (top + bottom - h) / 2
        return TrimRegion(x, y, x + w, y + h)
    }

    internal fun dragCorner(index: Int, x: Float, y: Float, ratio: Float?): TrimRegion {
        val leftEdge = index == 0 || index == 3
        val topEdge = index == 0 || index == 1
        val anchorX = if (leftEdge) right else left
        val anchorY = if (topEdge) bottom else top
        val signX = if (leftEdge) -1f else 1f
        val signY = if (topEdge) -1f else 1f
        val maxWidth = if (leftEdge) anchorX else 1f - anchorX
        val maxHeight = if (topEdge) anchorY else 1f - anchorY
        val requestedWidth = (x - anchorX) * signX
        val requestedHeight = (y - anchorY) * signY
        val w: Float
        val h: Float
        if (ratio == null) {
            w = requestedWidth.coerceIn(minOf(.04f, maxWidth), maxWidth)
            h = requestedHeight.coerceIn(minOf(.04f, maxHeight), maxHeight)
        } else {
            val limit = minOf(maxWidth, maxHeight * ratio)
            val minimum = minOf(limit, maxOf(.04f, .04f * ratio))
            w = ((requestedWidth + requestedHeight / ratio) / (1f + 1f / (ratio * ratio)))
                .coerceIn(minimum, limit)
            h = w / ratio
        }
        val movingX = (anchorX + signX * w).coerceIn(0f, 1f)
        val movingY = (anchorY + signY * h).coerceIn(0f, 1f)
        return TrimRegion(
            minOf(anchorX, movingX), minOf(anchorY, movingY),
            maxOf(anchorX, movingX), maxOf(anchorY, movingY)
        )
    }
}

enum class TrimRatio(val label: String) {
    FREE("Free"), ORIGINAL("Original"), A4("A4");

    internal fun normalized(imageWidth: Int, imageHeight: Int): Float? {
        val aspect = imageWidth.toFloat() / imageHeight
        return when (this) {
            FREE -> null
            ORIGINAL -> 1f
            A4 -> (if (aspect >= 1f) 297f / 210 else 210f / 297) / aspect
        }
    }
}

data class PageTrim(val region: TrimRegion = TrimRegion(), val degrees: Float = 0f, val ratio: TrimRatio = TrimRatio.FREE) {
    fun rotateRight(): PageTrim = copy(region = region.rotateRight())
}

internal fun straightenScale(width: Float, height: Float, degrees: Float): Float {
    val angle = degrees * PI / 180
    return (abs(cos(angle)) + abs(sin(angle)) * maxOf(width / height, height / width)).toFloat()
}
