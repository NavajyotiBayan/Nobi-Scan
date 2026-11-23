package com.nobi.scan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException

internal class PdfReadingSession private constructor(
    private val renderer: PdfRenderer,
    private val cacheCopy: File?
) : Closeable {
    val pageCount: Int = renderer.pageCount
    private var closed = false

    @Synchronized
    fun render(index: Int): Bitmap {
        check(!closed)
        return renderer.openPage(index).use { page ->
            val scale = 2000f / maxOf(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (exception: RuntimeException) {
                bitmap.recycle()
                throw exception
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            renderer.close()
        } finally {
            cacheCopy?.delete()
        }
    }

    companion object {
        suspend fun open(context: Context, library: DocumentLibrary, source: Uri): PdfReadingSession {
            try {
                val descriptor = context.contentResolver.openFileDescriptor(source, "r")
                    ?: throw IOException("Missing document")
                return PdfReadingSession(rendererFor(descriptor), null)
            } catch (exception: IllegalArgumentException) {
                // Some document providers return a pipe. PdfRenderer needs a seekable file.
                val local = File.createTempFile("reader-", ".pdf", context.cacheDir)
                try {
                    library.stagePdfInput(source, local)
                    val descriptor = ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY)
                    return PdfReadingSession(rendererFor(descriptor), local)
                } catch (failure: Exception) {
                    local.delete()
                    throw failure
                }
            }
        }

        private fun rendererFor(descriptor: ParcelFileDescriptor): PdfRenderer = try {
            PdfRenderer(descriptor)
        } catch (exception: Exception) {
            descriptor.close()
            throw exception
        }
    }
}
