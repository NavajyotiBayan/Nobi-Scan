package com.nobi.scan.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nobi.scan.cv.DocumentFilter
import com.nobi.scan.cv.DocumentProcessor
import com.nobi.scan.ui.camera.components.DocumentQuadData
import java.io.File

data class DraftPage(
    val source: File,
    val quad: DocumentQuadData,
    val filter: DocumentFilter = DocumentFilter.DOCUMENT,
    val turns: Int = 0,
    val trim: PageTrim = PageTrim()
) {
    fun loadSource(sampleSize: Int = 1): Bitmap =
        checkNotNull(BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }))

    fun rotateRight(): DraftPage = copy(turns = (turns + 1) % 4, trim = trim.rotateRight())

    fun render(sourceBitmap: Bitmap): Bitmap {
        val rectified = DocumentProcessor.rectify(sourceBitmap, quad, filter, turns)
        return try {
            val trimmed = DocumentProcessor.trim(rectified, trim)
            if (trimmed !== rectified) rectified.recycle()
            trimmed
        } catch (exception: Exception) {
            rectified.recycle()
            throw exception
        }
    }

    fun renderThumbnail(): Bitmap? {
        val original = loadSource(sampleSize = 8)
        return try {
            render(original)
        } catch (invalid: IllegalArgumentException) {
            null
        } finally {
            original.recycle()
        }
    }
}
