package com.nobi.scan.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.data.PageTrim
import com.nobi.scan.data.TrimRegion
import com.nobi.scan.data.straightenScale
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class DocumentFilter(val label: String, val saturation: Float, val contrast: Float) {
    ORIGINAL("Original", 1f, 1f),
    GRAYSCALE("Grayscale", 0f, 1f),
    DOCUMENT("Document", 0f, 1.6f),
    VIVID("Vivid", 1.35f, 1.16f)
}

object DocumentProcessor {
    fun rectify(source: Bitmap, quad: DocumentQuadData, filter: DocumentFilter, turns: Int): Bitmap {
        val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        val points = FloatArray(8)
        corners.forEachIndexed { index, point ->
            points[index * 2] = point.x * source.width
            points[index * 2 + 1] = point.y * source.height
        }
        for (index in corners.indices) {
            val a = corners[index]
            val b = corners[(index + 1) % 4]
            val c = corners[(index + 2) % 4]
            require((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > .0001f)
        }

        fun length(from: Int, to: Int) =
            hypot(points[from * 2] - points[to * 2], points[from * 2 + 1] - points[to * 2 + 1])

        val width = maxOf(length(0, 1), length(3, 2))
        val height = maxOf(length(0, 3), length(1, 2))
        val scale = minOf(1f, 2600f / maxOf(width, height))
        val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val w = outputWidth.toFloat()
        val h = outputHeight.toFloat()
        val transform = Matrix()
        require(transform.setPolyToPoly(
            points, 0,
            floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4
        ))

        val tone = ColorMatrix().apply { setSaturation(filter.saturation) }
        val contrast = filter.contrast
        val bias = 128f * (1f - contrast)
        tone.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, bias,
            0f, contrast, 0f, 0f, bias,
            0f, 0f, contrast, 0f, bias,
            0f, 0f, 0f, 1f, 0f
        )))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(tone) }
        val swapped = turns % 2 != 0
        val output = Bitmap.createBitmap(
            if (swapped) outputHeight else outputWidth,
            if (swapped) outputWidth else outputHeight,
            Bitmap.Config.ARGB_8888
        )
        Canvas(output).apply {
            drawColor(Color.WHITE)
            when (turns % 4) {
                1 -> translate(h, 0f)
                2 -> translate(w, h)
                3 -> translate(0f, w)
            }
            rotate(turns * 90f)
            drawBitmap(source, transform, paint)
        }
        return output
    }
    fun trim(source: Bitmap, edit: PageTrim): Bitmap {
        if (edit.region == TrimRegion() && edit.degrees == 0f) return source
        val region = edit.region
        val left = (region.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val top = (region.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (region.right * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (region.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
        val output = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        val centerX = source.width / 2f
        val centerY = source.height / 2f
        val cover = straightenScale(source.width.toFloat(), source.height.toFloat(), edit.degrees)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            translate(-left.toFloat(), -top.toFloat())
            scale(cover, cover, centerX, centerY)
            rotate(edit.degrees, centerX, centerY)
            drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }

}
