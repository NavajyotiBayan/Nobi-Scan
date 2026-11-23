package com.nobi.scan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal enum class CompressionQuality(val label: String, val description: String, val maxSide: Int, val jpegQuality: Int) {
    HIGH("High quality", "Keeps more image detail", 2400, 83),
    BALANCED("Balanced", "Reduces size while keeping good detail", 1800, 72),
    SMALL("Smallest", "Uses lower-resolution images", 1200, 57)
}

internal sealed interface CompressionProgress {
    data class Pages(val completed: Int, val total: Int) : CompressionProgress
    data object Writing : CompressionProgress
}

internal object PdfCompressor {
    private const val JPEG_PIXEL_BUDGET = 8_000_000L
    private const val OTHER_PIXEL_BUDGET = 4_000_000L

    /** Returns the original size when no image can be replaced. */
    suspend fun compress(
        context: Context,
        input: File,
        output: File,
        quality: CompressionQuality,
        onProgress: (CompressionProgress) -> Unit
    ): Long {
        PDFBoxResourceLoader.init(context)
        val scratch = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
        val job = coroutineContext
        return PDDocument.load(input, scratch).use { document ->
            if (document.isEncrypted) throw IOException("Encrypted PDFs are unsupported.")
            if (document.signatureDictionaries.isNotEmpty()) throw IOException("Signed PDFs are unsupported.")
            document.resourceCache = null
            val seen = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
            val replacements = IdentityHashMap<COSDictionary, PDImageXObject?>()
            val pageCount = document.numberOfPages
            onProgress(CompressionProgress.Pages(0, pageCount))
            for ((index, page) in document.pages.withIndex()) {
                job.ensureActive()
                page.resources?.let { compressResources(it, document, quality, seen, replacements) }
                onProgress(CompressionProgress.Pages(index + 1, pageCount))
            }
            job.ensureActive()
            if (replacements.values.none { it != null }) return@use input.length()

            onProgress(CompressionProgress.Writing)
            output.outputStream().buffered().use { file ->
                document.save(object : FilterOutputStream(file) {
                    override fun write(value: Int) {
                        job.ensureActive()
                        file.write(value)
                    }

                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        job.ensureActive()
                        file.write(bytes, offset, length)
                    }
                })
            }
            output.length()
        }
    }

    private suspend fun compressResources(
        resources: PDResources,
        document: PDDocument,
        quality: CompressionQuality,
        seen: MutableSet<COSDictionary>,
        replacements: MutableMap<COSDictionary, PDImageXObject?>
    ) {
        if (!seen.add(resources.cosObject)) return
        for (name in resources.xObjectNames.toList()) {
            coroutineContext.ensureActive()
            val xObject = try {
                resources.getXObject(name)
            } catch (exception: IOException) {
                continue
            }
            when (xObject) {
                is PDFormXObject -> xObject.resources?.let { compressResources(it, document, quality, seen, replacements) }
                is PDImageXObject -> {
                    val key = xObject.cosObject
                    if (!replacements.containsKey(key)) {
                        replacements[key] = try {
                            smallerImage(xObject, document, quality)
                        } catch (exception: IOException) {
                            null
                        } catch (exception: IllegalArgumentException) {
                            null
                        }
                    }
                    replacements[key]?.let { resources.put(name, it) }
                }
            }
        }
    }

    private fun smallerImage(
        image: PDImageXObject,
        document: PDDocument,
        quality: CompressionQuality
    ): PDImageXObject? {
        val stream = image.cosObject
        val currentBytes = stream.getLong(COSName.LENGTH, 0)
        val width = image.width
        val height = image.height
        val side = maxOf(width, height)
        if (currentBytes < 32_768 || minOf(width, height) < 1 || side < 600 || image.bitsPerComponent != 8 || image.isStencil ||
            stream.containsKey(COSName.MASK) || stream.containsKey(COSName.SMASK)) return null
        val color = image.colorSpace
        if (color !is PDDeviceRGB && color !is PDDeviceGray) return null

        val filters = image.stream.filters.orEmpty()
        val jpeg = filters.singleOrNull()
        val plainJpeg = (jpeg == COSName.DCT_DECODE || jpeg == COSName.DCT_DECODE_ABBREVIATION) &&
            !stream.containsKey(COSName.DECODE) && !stream.containsKey(COSName.DECODE_PARMS)
        val decoded = if (plainJpeg) {
            // PDFBox's DCT path decodes at full resolution even with a subsample request.
            // Read the standalone JPEG directly and ask Android to sample before allocation.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream.createRawInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth != width || bounds.outHeight != height || bounds.outMimeType != "image/jpeg") return null
            var sample = 1
            while (
                ((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample) >
                    JPEG_PIXEL_BUDGET || side.toLong() > 2L * quality.maxSide * sample
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            stream.createRawInputStream().use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            // Other filters can still inflate their full raster internally.
            if (width.toLong() * height > OTHER_PIXEL_BUDGET ||
                currentBytes > 8_388_608L || COSName.JPX_DECODE in filters ||
                COSName.DCT_DECODE in filters || COSName.DCT_DECODE_ABBREVIATION in filters) return null
            image.image
        } ?: return null
        var scaled = decoded
        try {
            val ratio = minOf(1f, quality.maxSide.toFloat() / maxOf(decoded.width, decoded.height))
            if (ratio < 1f) {
                scaled = Bitmap.createScaledBitmap(
                    decoded, (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                    (decoded.height * ratio).roundToInt().coerceAtLeast(1), true
                )
            }
            val jpeg = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, jpeg) ||
                jpeg.size() >= currentBytes) return null
            return JPEGFactory.createFromByteArray(document, jpeg.toByteArray()).apply {
                interpolate = image.interpolate
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}
