package com.nobi.scan.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal enum class OcrLanguage(val label: String, val models: String) {
    ENGLISH("English", "eng"),
    ASSAMESE("অসমীয়া (Assamese)", "asm"),
    HINDI("हिन्दी (Hindi)", "hin"),
    MIXED("Mixed · English, Assamese, Hindi", "eng+asm+hin")
}

internal data class OcrWord(val text: String, val bounds: Rect, val line: Int)

internal object OfflineOcr {
    private val recognition = Dispatchers.Default.limitedParallelism(1)

    suspend fun read(context: Context, page: Bitmap, language: OcrLanguage): String =
        recognize(context, page, language) { it.getUTF8Text().orEmpty().trim() }

    suspend fun detectWords(context: Context, page: Bitmap, language: OcrLanguage): List<OcrWord> =
        recognize(context, page, language) { engine ->
            if (engine.getUTF8Text().isNullOrBlank()) return@recognize emptyList()
            val iterator = engine.resultIterator ?: return@recognize emptyList()
            try {
                iterator.begin()
                var line = 0
                buildList {
                    do {
                        if (iterator.isAtBeginningOf(RIL_TEXTLINE)) line++
                        val text = iterator.getUTF8Text(RIL_WORD).orEmpty().trim()
                        val bounds = iterator.getBoundingRect(RIL_WORD)
                        if (text.isNotEmpty() && bounds != null && !bounds.isEmpty) {
                            add(OcrWord(text, bounds, line))
                        }
                    } while (iterator.next(RIL_WORD))
                }
            } finally {
                iterator.delete()
            }
        }

    private suspend fun <T> recognize(
        context: Context,
        page: Bitmap,
        language: OcrLanguage,
        result: (TessBaseAPI) -> T
    ): T =
        withContext(recognition) {
            val dataRoot = File(context.noBackupFilesDir, "ocr-fast-4.1.0")
            val models = File(dataRoot, "tessdata")
            if (!models.isDirectory && !models.mkdirs()) throw IOException("Could not prepare language models.")
            for (model in language.models.split('+')) {
                ensureActive()
                installModel(context, models, "$model.traineddata")
            }

            val engine = TessBaseAPI()
            try {
                if (!engine.init(dataRoot.absolutePath, language.models, TessBaseAPI.OEM_LSTM_ONLY)) {
                    throw IOException("Could not initialize text recognition.")
                }
                engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                engine.setImage(page)
                result(engine)
            } finally {
                engine.recycle()
            }
        }

    private fun installModel(context: Context, directory: File, filename: String) {
        val model = File(directory, filename)
        if (model.isFile && model.length() > 0) return

        val staged = File.createTempFile("model-", ".tmp", directory)
        try {
            context.assets.open("tessdata/$filename").use { source ->
                staged.outputStream().use { destination -> source.copyTo(destination) }
            }
            if (!staged.renameTo(model)) throw IOException("Could not install the language model.")
        } finally {
            staged.delete()
        }
    }
}
