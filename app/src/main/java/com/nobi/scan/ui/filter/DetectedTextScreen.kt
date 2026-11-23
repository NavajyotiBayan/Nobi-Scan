package com.nobi.scan.ui.filter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ocr.OcrWord
import com.nobi.scan.ocr.OfflineOcr
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.rememberNobiHaptics
import com.nobi.scan.ui.theme.LocalNobiPalette
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

@Composable
internal fun DetectedTextScreen(
    page: Bitmap,
    language: OcrLanguage,
    onLanguageChanged: (OcrLanguage) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberNobiHaptics()
    val palette = LocalNobiPalette.current
    var languagesOpen by remember { mutableStateOf(false) }
    var words by remember(page, language) { mutableStateOf<List<OcrWord>?>(null) }
    var failed by remember(page, language) { mutableStateOf(false) }
    var selected by remember(page, language) { mutableStateOf<IntRange?>(null) }
    val bitmap = remember(page) { page.asImageBitmap() }

    BackHandler(onBack = haptics.onTap(onClose))
    LaunchedEffect(page, language) {
        try {
            words = OfflineOcr.detectWords(context.applicationContext, page, language)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failed = true
        } catch (exception: LinkageError) {
            failed = true
        }
    }

    val recognized = words.orEmpty()
    val selection = selected?.let { recognized.slice(it) }.orEmpty()
    val text = buildString {
        selection.forEachIndexed { index, word ->
            if (index > 0) append(if (selection[index - 1].line == word.line) ' ' else '\n')
            append(word.text)
        }
    }

    AuraBackgroundBox {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = haptics.onTap(onClose)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to review")
                }
                Text("Detected text", style = MaterialTheme.typography.titleLarge, color = palette.ink)
            }
            Box {
                OutlinedButton(onClick = haptics.onTap { languagesOpen = true }) {
                    Text(language.label, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose OCR language")
                }
                DropdownMenu(expanded = languagesOpen, onDismissRequest = haptics.onTap { languagesOpen = false }) {
                    OcrLanguage.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                if (option != language) {
                                    haptics.step()
                                    onLanguageChanged(option)
                                }
                                languagesOpen = false
                            }
                        )
                    }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(palette.surface.copy(alpha = .48f), RoundedCornerShape(20.dp))
                    .padding(8.dp)
            ) {
                Image(bitmap, contentDescription = "Corrected scan", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                val accent = palette.accent
                Canvas(
                    Modifier.fillMaxSize().pointerInput(page, recognized) {
                        val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = hitWord(down.position, recognized, canvasSize, page, 8.dp.toPx())
                            if (start < 0) return@awaitEachGesture
                            down.consume()
                            selected = start..start
                            haptics.step()
                            var end = start
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val next = hitWord(change.position, recognized, canvasSize, page, 12.dp.toPx())
                                if (next >= 0 && next != end) {
                                    haptics.step()
                                    end = next
                                    selected = minOf(start, end)..maxOf(start, end)
                                }
                                change.consume()
                            } while (true)
                        }
                    }
                ) {
                    val outline = 1.dp.toPx()
                    recognized.forEachIndexed { index, word ->
                        val bounds = wordBounds(word, size, page)
                        val active = selected?.contains(index) == true
                        drawRect(accent.copy(alpha = if (active) .45f else .12f), bounds.topLeft, bounds.size)
                        drawRect(accent.copy(alpha = if (active) 1f else .55f), bounds.topLeft, bounds.size, style = Stroke(outline))
                    }
                }
            }

            val status = when {
                failed -> "Could not detect text on this page."
                words == null -> "Reading this page on your device…"
                recognized.isEmpty() -> "No text found. Check the page crop and language."
                selection.isEmpty() -> "Tap a word or drag across text to select it."
                else -> "${selection.size} selected"
            }
            Text(status, color = palette.mutedInk, style = MaterialTheme.typography.bodySmall)
            if (words == null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = recognized.isNotEmpty(),
                    onClick = haptics.onTap { selected = recognized.indices }
                ) { Text("Select all") }
                TextButton(enabled = text.isNotEmpty(), onClick = {
                    copyRecognizedText(context, text)
                    haptics.confirm()
                }) { Text("Copy") }
                TextButton(enabled = text.isNotEmpty(), onClick = haptics.onTap {
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        context.startActivity(Intent.createChooser(intent, null))
                    } catch (exception: ActivityNotFoundException) {
                        Toast.makeText(context, "No sharing app available", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Share") }
            }
        }
    }
}

private fun wordBounds(word: OcrWord, viewport: Size, page: Bitmap): Rect {
    val scale = minOf(viewport.width / page.width, viewport.height / page.height)
    val left = (viewport.width - page.width * scale) / 2
    val top = (viewport.height - page.height * scale) / 2
    val area = word.bounds
    return Rect(
        left + area.left * scale,
        top + area.top * scale,
        left + area.right * scale,
        top + area.bottom * scale
    )
}

private fun hitWord(point: Offset, words: List<OcrWord>, viewport: Size, page: Bitmap, margin: Float): Int {
    var best = -1
    var distance = Float.POSITIVE_INFINITY
    words.forEachIndexed { index, word ->
        val bounds = wordBounds(word, viewport, page)
        if (bounds.contains(point)) return index
        if (bounds.inflate(margin).contains(point)) {
            val dx = abs(point.x - bounds.center.x) - bounds.width / 2
            val dy = abs(point.y - bounds.center.y) - bounds.height / 2
            val edgeDistance = maxOf(0f, dx) + maxOf(0f, dy)
            if (edgeDistance < distance) {
                best = index
                distance = edgeDistance
            }
        }
    }
    return best
}
