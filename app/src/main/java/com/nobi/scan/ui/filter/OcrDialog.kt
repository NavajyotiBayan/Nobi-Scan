package com.nobi.scan.ui.filter

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ocr.OfflineOcr
import com.nobi.scan.ui.components.rememberNobiHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OcrDialog(
    page: Bitmap,
    language: OcrLanguage,
    onLanguageChanged: (OcrLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberNobiHaptics()
    val scope = rememberCoroutineScope()
    var languagesOpen by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun recognize() {
        if (running) return
        haptics.tap()
        failed = false
        result = null
        running = true
        scope.launch {
            try {
                result = OfflineOcr.read(context.applicationContext, page, language)
                if (!result.isNullOrBlank()) haptics.confirm()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failed = true
            } catch (exception: LinkageError) {
                failed = true
            } finally {
                running = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = haptics.onTap(onDismiss),
        title = { Text("Recognize text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(
                        enabled = !running,
                        onClick = haptics.onTap { languagesOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(language.label, Modifier.weight(1f))
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
                                        result = null
                                        failed = false
                                    }
                                    languagesOpen = false
                                }
                            )
                        }
                    }
                }
                val text = result
                when {
                    running -> {
                        Text("Reading this page on your device…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    failed -> Text("Could not recognize this page. Try again.", color = MaterialTheme.colorScheme.error)
                    text == null -> Text("Choose the language printed on this page.")
                    text.isBlank() -> Text("No text found. Check the page orientation, crop, and language.")
                    else -> SelectionContainer {
                        Text(
                            text,
                            modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = haptics.onTap(onDismiss)) { Text("Close") } },
        confirmButton = {
            val text = result
            if (!text.isNullOrBlank()) {
                TextButton(onClick = {
                    copyRecognizedText(context, text)
                    haptics.confirm()
                }) { Text("Copy text") }
            } else {
                TextButton(enabled = !running, onClick = ::recognize) { Text("Recognize") }
            }
        }
    )
}

internal fun copyRecognizedText(context: Context, text: String) {
    val clip = ClipData.newPlainText("Recognized text", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
}
