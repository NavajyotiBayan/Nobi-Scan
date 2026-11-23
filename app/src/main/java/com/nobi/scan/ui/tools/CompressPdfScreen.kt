package com.nobi.scan.ui.tools

import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.CompressionProgress
import com.nobi.scan.data.CompressionQuality
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.data.PdfCompressor
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.PdfNameDialog
import com.nobi.scan.ui.components.rememberNobiHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

private data class CompressionSource(val file: File, val name: String, val bytes: Long)
private data class CompressionPreview(val file: File, val bytes: Long)

@Composable
internal fun CompressPdfScreen(library: DocumentLibrary, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberNobiHaptics()
    val compressionDispatcher = remember { Dispatchers.IO.limitedParallelism(1) }
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var quality by rememberSaveable { mutableStateOf(CompressionQuality.BALANCED) }
    var chooseLibrary by rememberSaveable { mutableStateOf(false) }
    var input by remember(source, revision) { mutableStateOf<CompressionSource?>(null) }
    var preview by remember(input, quality) { mutableStateOf<CompressionPreview?>(null) }
    var loadError by remember(source, revision) { mutableStateOf<String?>(null) }
    var previewError by remember(input, quality) { mutableStateOf<String?>(null) }
    var progress by remember(input, quality) { mutableStateOf<CompressionProgress?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }

    val cancel = haptics.onTap {
        source = null
        revision++
    }

    fun open(uri: Uri) {
        source = uri
        revision++
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }

    LaunchedEffect(source, revision) {
        val uri = source ?: return@LaunchedEffect
        var staged: File? = null
        try {
            input = withContext(Dispatchers.IO) {
                val file = File.createTempFile("compress-input-", ".pdf", context.cacheDir)
                staged = file
                library.stagePdfInput(uri, file)
                CompressionSource(file, library.pdfDisplayName(uri), file.length())
            }
            awaitCancellation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            loadError = "Could not open this PDF."
        } finally {
            staged?.delete()
        }
    }

    LaunchedEffect(input, quality) {
        val selected = input ?: return@LaunchedEffect
        var staged: File? = null
        try {
            preview = withContext(compressionDispatcher) {
                val file = File.createTempFile("compress-preview-", ".pdf", context.cacheDir)
                staged = file
                val bytes = PdfCompressor.compress(
                    context.applicationContext, selected.file, file, quality
                ) { progress = it }
                CompressionPreview(file, bytes)
            }
            awaitCancellation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            previewError = exception.message?.takeIf { it == "Encrypted PDFs are unsupported." || it == "Signed PDFs are unsupported." }
                ?: "Could not prepare a compressed copy."
        } catch (exception: RuntimeException) {
            previewError = "Could not prepare a compressed copy."
        } finally {
            staged?.delete()
        }
    }

    fun save(share: Boolean) {
        val output = preview ?: return
        if (saving || output.bytes >= (input?.bytes ?: 0L)) return
        saveError = library.nameError(name)
        if (saveError != null) return
        val outputName = name
        saving = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { library.saveCompressedPdf(output.file, outputName) }
                haptics.confirm()
                Toast.makeText(context, "Saved ${saved.name}", Toast.LENGTH_SHORT).show()
                if (share) {
                    try {
                        context.startActivity(library.shareIntent(saved))
                    } catch (exception: ActivityNotFoundException) {
                        Toast.makeText(context, "No sharing app available", Toast.LENGTH_SHORT).show()
                    }
                }
                onSaved()
            } catch (exception: IOException) {
                saveError = "Could not save this PDF."
            } finally {
                saving = false
            }
        }
    }

    BackHandler(onBack = haptics.onTapIfEnabled(!saving, onClose))
    if (chooseLibrary) {
        LibraryPdfPicker(
            library = library, included = emptySet(), onAdd = { open(it.single()) },
            onDismiss = { chooseLibrary = false }, singleSelection = true
        )
    }
    if (naming) {
        PdfNameDialog(
            title = "Name compressed copy", name = name, busy = saving, error = saveError,
            onNameChange = { name = it; saveError = null },
            confirmLabel = "Save PDF", onConfirm = { save(false) }, onShare = { save(true) },
            onDismiss = { if (!saving) naming = false }
        )
    }

    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = haptics.onTap(onClose), enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                }
                Text("Compress PDF", style = MaterialTheme.typography.headlineSmall)
            }
            val selected = input
            if (selected == null) {
                val error = loadError
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    source != null -> {
                        Text("Reading PDF…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = cancel) { Text("Cancel") }
                    }
                    else -> Text("Choose a PDF to compress.")
                }
                val canChoose = source == null || error != null
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = haptics.onTap { chooseLibrary = true }, enabled = canChoose,
                        modifier = Modifier.weight(1f)) { Text("Library") }
                    OutlinedButton(onClick = haptics.onTap {
                        try {
                            picker.launch(arrayOf("application/pdf"))
                        } catch (exception: ActivityNotFoundException) {
                            Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = canChoose, modifier = Modifier.weight(1f)) { Text("Browse files") }
                }
            } else {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Column(Modifier.selectableGroup()) {
                    for (option in CompressionQuality.entries) {
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = quality == option, enabled = !saving, role = Role.RadioButton,
                                onClick = {
                                    if (quality != option) {
                                        haptics.step()
                                        quality = option
                                    }
                                }
                            ).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = quality == option, onClick = null)
                            Column {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(option.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val original = Formatter.formatFileSize(context, selected.bytes)
                Text("Original · $original")
                val result = preview
                val error = previewError
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    result == null -> {
                        when (val stage = progress) {
                            is CompressionProgress.Pages -> {
                                Text("Processed ${stage.completed} of ${stage.total} pages")
                                LinearProgressIndicator(
                                    progress = { stage.completed.toFloat() / stage.total.coerceAtLeast(1) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            CompressionProgress.Writing -> {
                                Text("Writing compressed PDF…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            null -> {
                                Text("Opening PDF for compression…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(onClick = cancel) { Text("Cancel") }
                    }
                    result.bytes >= selected.bytes -> Text("This preset could not make the PDF smaller.")
                    else -> {
                        val compressed = Formatter.formatFileSize(context, result.bytes)
                        val reduction = ((1.0 - result.bytes.toDouble() / selected.bytes) * 100).roundToInt()
                        Text("Compressed · $compressed · $reduction% smaller")
                    }
                }
                Button(
                    onClick = haptics.onTap {
                        name = selected.name.substringBeforeLast('.', selected.name).take(40) + " compressed"
                        saveError = null
                        naming = true
                    },
                    enabled = !saving && result != null && result.bytes < selected.bytes,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) { Text("Save compressed copy") }
            }
        }
    }
}
