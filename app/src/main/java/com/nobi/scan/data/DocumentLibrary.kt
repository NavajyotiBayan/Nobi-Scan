package com.nobi.scan.data

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.net.Uri
import androidx.core.content.FileProvider
import com.nobi.scan.cv.DocumentFilter
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentLibrary(private val context: Context) {
    private val legacyDirectory = File(context.filesDir, "documents")
    private val directory by lazy {
        val documents = File(context.filesDir, "Nobi Scan/Documents")
        if (documents.isDirectory || documents.mkdirs()) {
            legacyDirectory.listFiles(::isPdf).orEmpty().forEach { file ->
                val target = nextPdfPath(documents, file.nameWithoutExtension)
                if (!file.renameTo(target)) {
                    android.util.Log.w("NobiScan", "A legacy PDF could not be moved")
                }
            }
            // This only removes an empty directory. Unmoved files remain accessible.
            legacyDirectory.delete()
        }
        documents
    }

    private fun isPdf(file: File): Boolean = file.isFile && file.extension == "pdf"

    private fun isLibraryPdf(file: File): Boolean =
        (file.parentFile == directory || file.parentFile == legacyDirectory) && file.extension == "pdf"

    private fun nextPdfPath(folder: File, base: String, otherFolder: File? = null): File {
        var target = File(folder, "$base.pdf")
        var suffix = 2
        while (target.exists() || otherFolder?.let { File(it, target.name).exists() } == true) {
            target = File(folder, "$base (${suffix++}).pdf")
        }
        return target
    }

    @Synchronized
    fun list(): List<File> = (
        directory.listFiles(::isPdf).orEmpty().asList() +
            legacyDirectory.listFiles(::isPdf).orEmpty().asList()
        ).sortedByDescending(File::lastModified)

    fun draftPage(bitmap: Bitmap): File {
        val file = File.createTempFile("scan-", ".jpg", context.cacheDir)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it)) }
            return file
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
    }

    fun saveDraft(pages: List<DraftPage>, name: String): File {
        val rendered = mutableListOf<File>()
        try {
            for (page in pages) {
                val source = page.loadSource()
                try {
                    val corrected = page.render(source)
                    try {
                        rendered.add(draftPage(corrected))
                    } finally {
                        corrected.recycle()
                    }
                } finally {
                    source.recycle()
                }
            }
            return save(rendered, name)
        } finally {
            rendered.forEach { it.delete() }
        }
    }

    internal suspend fun saveSearchableDraft(
        pages: List<DraftPage>,
        name: String,
        language: OcrLanguage,
        onPage: suspend (Int) -> Unit
    ): Pair<File, Int> {
        require(pages.isNotEmpty())
        val temporary = newOutput(name)
        try {
            val pagesWithText = SearchablePdfWriter.write(context, pages, language, temporary, onPage)
            coroutineContext.ensureActive()
            return publishOutput(temporary, name) to pagesWithText
        } finally {
            temporary.delete()
        }
    }

    fun suggestedName(): String =
        SimpleDateFormat("'Scan' yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(Date())

    fun nameError(input: String): String? {
        val name = pdfBaseName(input)
        return when {
            name.isBlank() || name.startsWith('.') -> "Enter a document name."
            name.any { it == '/' || it == '\\' || it.isISOControl() } -> "Remove slashes and control characters."
            name.toByteArray(Charsets.UTF_8).size > 200 -> "Use a shorter name."
            else -> null
        }
    }

    private fun pdfBaseName(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed.dropLast(4).trimEnd() else trimmed
    }

    @Synchronized
    fun rename(file: File, input: String): String? {
        nameError(input)?.let { return it }
        if (!isLibraryPdf(file) || !file.isFile) {
            return "This PDF is no longer available."
        }
        val folder = checkNotNull(file.parentFile)
        val target = File(folder, "${pdfBaseName(input)}.pdf")
        if (target == file) return null
        val otherFolder = if (folder == directory) legacyDirectory else directory
        if (target.exists() || File(otherFolder, target.name).exists()) return "A PDF with that name already exists."
        return if (file.renameTo(target)) null else "Could not rename this PDF."
    }

    private fun save(pages: List<File>, name: String): File {
        require(pages.isNotEmpty())
        val temporary = newOutput(name)
        try {
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, file ->
                    val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path))
                    try {
                        val portrait = bitmap.height >= bitmap.width
                        val width = if (portrait) 595 else 842
                        val height = if (portrait) 842 else 595
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                        )
                        try {
                            val scale = minOf((width - 24f) / bitmap.width, (height - 24f) / bitmap.height)
                            val imageWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                            val imageHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                            val left = (width - imageWidth) / 2
                            val top = (height - imageHeight) / 2
                            page.canvas.drawColor(Color.WHITE)
                            page.canvas.drawBitmap(bitmap, null,
                                Rect(left, top, left + imageWidth, top + imageHeight),
                                Paint(Paint.FILTER_BITMAP_FLAG))
                        } finally {
                            document.finishPage(page)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                temporary.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            return publishOutput(temporary, name)
        } finally {
            temporary.delete()
        }
    }

    suspend fun mergePdfs(sources: List<Uri>, name: String): File {
        require(sources.size >= 2)
        val temporary = newOutput(name)
        try {
            PDFBoxResourceLoader.init(context)
            val scratch = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            val merger = PDFMergerUtility()
            PDDocument(scratch).use { destination ->
                sources.forEachIndexed { index, uri ->
                    coroutineContext.ensureActive()
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Missing PDF stream")
                        stream.use {
                            PDDocument.load(it, scratch).use { source ->
                                if (source.isEncrypted) throw IOException("PDF ${index + 1} is encrypted.")
                                if (source.numberOfPages == 0) throw IOException("PDF ${index + 1} has no pages.")
                                merger.appendDocument(destination, source)
                            }
                        }
                    } catch (exception: InvalidPasswordException) {
                        throw IOException("PDF ${index + 1} is password-protected.", exception)
                    } catch (exception: IOException) {
                        throw IOException("Could not merge PDF ${index + 1}. Check that it is readable and unencrypted.", exception)
                    } catch (exception: SecurityException) {
                        throw IOException("Access to PDF ${index + 1} expired. Remove it and add it again.", exception)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: RuntimeException) {
                        throw IOException("Could not read PDF ${index + 1}.", exception)
                    }
                }
                destination.save(temporary)
            }
            coroutineContext.ensureActive()
            return publishOutput(temporary, name)
        } finally {
            temporary.delete()
        }
    }

    suspend fun saveCompressedPdf(candidate: File, name: String): File {
        val temporary = newOutput(name)
        try {
            candidate.copyTo(temporary, overwrite = true)
            coroutineContext.ensureActive()
            return publishOutput(temporary, name)
        } finally {
            temporary.delete()
        }
    }

    fun newPageEditorInput(): File = File.createTempFile("organize-", ".pdf", context.cacheDir)

    suspend fun stagePdfInput(uri: Uri, target: File) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open this PDF.")
        stream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    fun pdfPageCount(input: File): Int = openPdf(input).use { it.pageCount }

    fun pdfDisplayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Document.pdf"

    fun pageThumbnail(file: File, index: Int): Bitmap =
        openPdf(file).use { pdf -> pdf.openPage(index).use { renderThumbnail(it, 240f) } }

    suspend fun saveOrganizedPages(input: File, order: List<Int>, name: String): File =
        savePageCopies(input, listOf(order to name)).single()

    fun splitPartName(name: String, part: Int): String = "${pdfBaseName(name)} - Part $part"

    suspend fun splitPdf(input: File, parts: List<List<Int>>, name: String): List<File> {
        require(parts.size >= 2 && nameError(name) == null)
        return savePageCopies(input, parts.mapIndexed { index, pages ->
            pages to splitPartName(name, index + 1)
        })
    }

    private suspend fun savePageCopies(input: File, copies: List<Pair<List<Int>, String>>): List<File> {
        val staged = mutableListOf<Pair<File, String>>()
        try {
            PDFBoxResourceLoader.init(context)
            val scratch = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            PDDocument.load(input, scratch).use { source ->
                if (source.isEncrypted) throw IOException("Encrypted PDFs are unsupported.")
                val pages = source.pages.toList()
                for ((order, name) in copies) {
                    coroutineContext.ensureActive()
                    require(order.isNotEmpty() && order.size == order.toSet().size)
                    if (order.any { it !in pages.indices }) {
                        throw IOException("One of the selected pages is unavailable.")
                    }
                    val temporary = newOutput(name)
                    staged.add(temporary to name)
                    PDDocument(scratch).use { output ->
                        output.version = source.version
                        val cloner = PDFCloneUtility(output)
                        for (index in order) {
                            coroutineContext.ensureActive()
                            val page = pages[index]
                            // Materialize inherited values and keep the copied page independent of the source page tree.
                            val content = COSDictionary().apply {
                                setItem(COSName.TYPE, COSName.PAGE)
                                setItem(COSName.CONTENTS, page.cosObject.getItem(COSName.CONTENTS))
                                setItem(COSName.RESOURCES, page.resources)
                                setItem(COSName.MEDIA_BOX, page.mediaBox)
                                setItem(COSName.CROP_BOX, page.cropBox)
                                setItem(COSName.BLEED_BOX, page.bleedBox)
                                setItem(COSName.TRIM_BOX, page.trimBox)
                                setItem(COSName.ART_BOX, page.artBox)
                                setItem(COSName.GROUP, page.cosObject.getItem(COSName.GROUP))
                                setItem(COSName.USER_UNIT, page.cosObject.getItem(COSName.USER_UNIT))
                                setInt(COSName.ROTATE, page.rotation)
                            }
                            output.addPage(PDPage(cloner.cloneForNewDocument(content) as COSDictionary))
                        }
                        output.save(temporary)
                    }
                }
            }
            coroutineContext.ensureActive()
            return publishPageCopies(staged)
        } finally {
            staged.forEach { (temporary, _) -> temporary.delete() }
        }
    }

    @Synchronized
    private fun publishPageCopies(staged: List<Pair<File, String>>): List<File> {
        val published = mutableListOf<File>()
        try {
            for ((temporary, name) in staged) {
                published.add(publishOutput(temporary, name))
            }
        } catch (exception: Exception) {
            published.forEach { it.delete() }
            throw exception
        }
        return published
    }

    private fun newOutput(name: String): File {
        require(nameError(name) == null)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not open the document library.")
        }
        return File.createTempFile("writing-", ".tmp", directory)
    }

    @Synchronized
    private fun publishOutput(temporary: File, name: String): File {
        val target = nextPdfPath(directory, pdfBaseName(name), legacyDirectory)
        if (!temporary.renameTo(target)) throw IOException("Could not save the PDF.")
        return target
    }

    fun editCopy(file: File): List<DraftPage> {
        require(isLibraryPdf(file))
        val draft = mutableListOf<DraftPage>()
        try {
            openPdf(file).use { pdf ->
                require(pdf.pageCount > 0)
                repeat(pdf.pageCount) { index ->
                    pdf.openPage(index).use { page ->
                        val scale = 2400f / maxOf(page.width, page.height)
                        val image = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            image.eraseColor(Color.WHITE)
                            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            draft += DraftPage(draftPage(image), DocumentQuadData.fullPage(), DocumentFilter.ORIGINAL)
                        } finally {
                            image.recycle()
                        }
                    }
                }
            }
            return draft
        } catch (exception: Exception) {
            draft.forEach { it.source.delete() }
            throw exception
        }
    }

    private fun openPdf(file: File): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(descriptor)
        } catch (exception: Exception) {
            descriptor.close()
            throw exception
        }
    }

    data class Preview(val thumbnail: Bitmap, val pages: Int)

    private data class PreviewKey(val path: String, val modified: Long, val size: Long)

    private val previews = object : LruCache<PreviewKey, Preview>(4 * 1024 * 1024) {
        override fun sizeOf(key: PreviewKey, value: Preview): Int = value.thumbnail.allocationByteCount
    }

    fun preview(file: File): Preview? {
        val key = PreviewKey(file.path, file.lastModified(), file.length())
        previews.get(key)?.let { return it }
        return renderPreview(file)?.also { previews.put(key, it) }
    }

    private fun renderPreview(file: File): Preview? {
        return try {
            openPdf(file).use { pdf ->
                if (pdf.pageCount == 0) return null
                pdf.openPage(0).use { page ->
                    Preview(renderThumbnail(page, 512f), pdf.pageCount)
                }
            }
        } catch (exception: java.io.IOException) {
            null
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: SecurityException) {
            null
        }
    }

    private fun renderThumbnail(page: PdfRenderer.Page, maxSide: Float): Bitmap {
        val scale = maxSide / maxOf(page.width, page.height)
        val thumbnail = Bitmap.createBitmap(
            (page.width * scale).toInt().coerceAtLeast(1),
            (page.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        try {
            thumbnail.eraseColor(Color.WHITE)
            page.render(thumbnail, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return thumbnail
        } catch (exception: RuntimeException) {
            thumbnail.recycle()
            throw exception
        }
    }

    fun uri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.documents", file)

    fun shareIntent(file: File): Intent {
        val uri = uri(file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, null)
    }

    @Synchronized
    fun delete(file: File): Boolean {
        if (!isLibraryPdf(file)) return false
        return file.delete() || !file.exists()
    }

    fun export(file: File, destination: Uri) {
        (context.contentResolver.openOutputStream(destination, "wt")
            ?: throw java.io.IOException("Export output stream unavailable")).use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
    }
}
