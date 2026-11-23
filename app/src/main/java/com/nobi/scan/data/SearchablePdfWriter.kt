package com.nobi.scan.data

import android.content.Context
import android.graphics.Bitmap
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ocr.OcrWord
import com.nobi.scan.ocr.OfflineOcr
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal object SearchablePdfWriter {
    suspend fun write(
        context: Context,
        pages: List<DraftPage>,
        language: OcrLanguage,
        output: File,
        onPage: suspend (Int) -> Unit
    ): Int {
        PDFBoxResourceLoader.init(context)
        val scratch = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
        return PDDocument(scratch).use { document ->
            val fonts = mutableMapOf<String, PDType0Font>()
            var pagesWithText = 0
            for ((index, draft) in pages.withIndex()) {
                coroutineContext.ensureActive()
                onPage(index + 1)
                val bitmap = correctedPage(draft)
                try {
                    val words = OfflineOcr.detectWords(context, bitmap, language)
                    val paper = if (bitmap.height >= bitmap.width) PDRectangle.A4
                        else PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                    val scale = minOf((paper.width - 24) / bitmap.width, (paper.height - 24) / bitmap.height)
                    val left = (paper.width - bitmap.width * scale) / 2
                    val bottom = (paper.height - bitmap.height * scale) / 2
                    val page = PDPage(paper)
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        bitmap.setHasAlpha(false)
                        val image = JPEGFactory.createFromImage(document, bitmap, .93f)
                        stream.drawImage(image, left, bottom, bitmap.width * scale, bitmap.height * scale)
                        val top = paper.height - bottom
                        if (writeText(stream, words, left, top, scale) { name ->
                            fonts.getOrPut(name) {
                                context.assets.open("fonts/$name-Regular.ttf").use { PDType0Font.load(document, it) }
                            }
                        }) pagesWithText++
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            if (pagesWithText == 0) throw IOException("No text was recognized. Try another language or adjust the crop.")
            coroutineContext.ensureActive()
            document.save(output)
            pagesWithText
        }
    }

    private fun correctedPage(draft: DraftPage): Bitmap {
        val source = draft.loadSource()
        return try {
            draft.render(source)
        } finally {
            source.recycle()
        }
    }

    private fun writeText(
        stream: PDPageContentStream,
        words: List<OcrWord>,
        left: Float,
        top: Float,
        scale: Float,
        font: (String) -> PDType0Font
    ): Boolean {
        if (words.isEmpty()) return false
        var written = false
        stream.beginText()
        stream.setRenderingMode(RenderingMode.NEITHER)
        for (line in words.groupBy { it.line }.values) {
            val lineHeight = line.maxOf { it.bounds.bottom } - line.minOf { it.bounds.top }
            val fontSize = (lineHeight * scale).coerceAtLeast(1f)
            val baseline = top - (line.maxOf { it.bounds.bottom } - lineHeight * .16f) * scale
            for (word in line) {
                val primary = when {
                    word.text.any { it in '\u0980'..'\u09FF' } -> "NotoSansBengali"
                    word.text.any { it in '\u0900'..'\u097F' } -> "NotoSansDevanagari"
                    else -> "NotoSans"
                }
                val typeface = font(primary)
                var text = word.text
                val encodedWidth = try {
                    typeface.getStringWidth(text)
                } catch (exception: IllegalArgumentException) {
                    text = text.codePoints().toArray().mapNotNullCodePoint { candidate ->
                        try {
                            typeface.getStringWidth(candidate)
                            true
                        } catch (exception: IllegalArgumentException) {
                            false
                        }
                    }
                    typeface.getStringWidth(text)
                }
                if (text.isEmpty() || encodedWidth <= 0f) continue
                val horizontal = word.bounds.width() * scale / (encodedWidth * fontSize / 1000f)
                stream.setFont(typeface, fontSize)
                stream.setTextMatrix(Matrix(horizontal, 0f, 0f, 1f, left + word.bounds.left * scale, baseline))
                stream.showText(text)
                written = true
            }
        }
        stream.endText()
        return written
    }

    private fun IntArray.mapNotNullCodePoint(keep: (String) -> Boolean): String = buildString {
        for (codePoint in this@mapNotNullCodePoint) {
            val character = String(Character.toChars(codePoint))
            if (keep(character)) append(character)
        }
    }
}
