package com.nobi.scan.ui.tools

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.DocumentLibrary
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

private data class OpenPdf(val file: File, val name: String, val pageCount: Int)
private enum class PageExport { ORGANIZED, EXTRACTED }

@Composable
internal fun OrganizePdfScreen(library: DocumentLibrary, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberNobiHaptics()
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var sourceRevision by rememberSaveable { mutableIntStateOf(0) }
    var order by rememberSaveable { mutableStateOf(arrayListOf<Int>()) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<Int>()) }
    var chooseLibrary by rememberSaveable { mutableStateOf(false) }
    var operation by rememberSaveable { mutableStateOf<PageExport?>(null) }
    var splitting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var openPdf by remember { mutableStateOf<OpenPdf?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Int?>(null) }

    fun open(uri: Uri) {
        openPdf = null
        loadError = null
        order = arrayListOf()
        selected = arrayListOf()
        source = uri
        sourceRevision++
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }

    LaunchedEffect(source, sourceRevision) {
        val uri = source ?: return@LaunchedEffect
        var staged: File? = null
        try {
            val pdf = withContext(Dispatchers.IO) {
                val local = library.newPageEditorInput()
                staged = local
                library.stagePdfInput(uri, local)
                val count = library.pdfPageCount(local)
                if (count == 0) throw IOException("This PDF has no pages.")
                OpenPdf(local, library.pdfDisplayName(uri), count)
            }
            if (order.isEmpty() || order.any { it !in 0 until pdf.pageCount }) {
                order = ArrayList((0 until pdf.pageCount).toList())
                selected = arrayListOf()
            }
            openPdf = pdf
            awaitCancellation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            loadError = "Could not open this PDF. Check that it is readable and unencrypted."
            operation = null
            splitting = false
        } finally {
            staged?.delete()
        }
    }

    BackHandler {
        if (!busy) {
            haptics.tap()
            onClose()
        }
    }

    fun move(page: Int, direction: Int): Boolean {
        val from = order.indexOf(page)
        val to = from + direction
        if (busy || from < 0 || to !in order.indices) return false
        order = ArrayList(order).apply { add(to, removeAt(from)) }
        haptics.step()
        return true
    }

    fun nameOutput(pdf: OpenPdf, format: PageExport) {
        val base = pdf.name.substringBeforeLast('.', pdf.name).take(48)
        name = "$base ${if (format == PageExport.EXTRACTED) "extracted" else "organized"}"
        saveError = null
        operation = format
    }

    fun save(share: Boolean) {
        val pdf = openPdf ?: return
        val pages = if (operation == PageExport.EXTRACTED) order.filter { it in selected } else order.toList()
        if (busy || pages.isEmpty()) return
        saveError = library.nameError(name)
        if (saveError != null) return
        busy = true
        val outputName = name
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) { library.saveOrganizedPages(pdf.file, pages, outputName) }
                haptics.confirm()
                Toast.makeText(context, "Saved ${output.name}", Toast.LENGTH_SHORT).show()
                if (share) {
                    try {
                        context.startActivity(library.shareIntent(output))
                    } catch (exception: ActivityNotFoundException) {
                        Toast.makeText(context, "No sharing app available", Toast.LENGTH_SHORT).show()
                    }
                }
                onSaved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                saveError = exception.message ?: "Could not save this PDF."
            } catch (exception: RuntimeException) {
                saveError = "Could not copy the selected pages."
            } finally {
                busy = false
            }
        }
    }

    fun split(ranges: List<IntRange>, baseName: String) {
        val pdf = openPdf ?: return
        if (busy) return
        val parts = ranges.map { range -> range.map { order[it - 1] } }
        busy = true
        saveError = null
        scope.launch {
            try {
                val outputs = withContext(Dispatchers.IO) { library.splitPdf(pdf.file, parts, baseName) }
                haptics.confirm()
                Toast.makeText(context, "Saved ${outputs.size} PDFs", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                saveError = exception.message ?: "Could not save the split PDFs."
            } catch (exception: RuntimeException) {
                saveError = "Could not split this PDF."
            } finally {
                busy = false
            }
        }
    }

    if (chooseLibrary) {
        LibraryPdfPicker(
            library = library,
            included = emptySet(),
            onAdd = { open(it.single()) },
            onDismiss = { chooseLibrary = false },
            singleSelection = true
        )
    }
    val loadedPdf = openPdf
    if (splitting && loadedPdf != null) {
        SplitPdfDialog(
            library = library,
            filename = loadedPdf.name,
            pageCount = order.size,
            busy = busy,
            saveError = saveError,
            onSave = ::split,
            onDismiss = { splitting = false }
        )
    }
    val activeOperation = operation
    if (openPdf != null && activeOperation != null) {
        PdfNameDialog(
            title = when {
                busy -> "Saving PDF…"
                activeOperation == PageExport.EXTRACTED -> "Name extracted PDF"
                else -> "Name organized copy"
            },
            name = name,
            onNameChange = {
                name = it
                saveError = null
            },
            busy = busy,
            error = saveError,
            confirmLabel = "Save PDF",
            onConfirm = { save(share = false) },
            onShare = { save(share = true) },
            onDismiss = { if (!busy) operation = null }
        )
    }

    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = haptics.onTap(onClose), enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                }
                Text("Organize PDF", style = MaterialTheme.typography.headlineSmall)
            }
            val pdf = openPdf
            if (pdf == null) {
                val error = loadError
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    source != null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Text("Choose a PDF to organize its pages.")
                }
                val canChoose = source == null || error != null
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = haptics.onTap { chooseLibrary = true },
                        enabled = canChoose,
                        modifier = Modifier.weight(1f)
                    ) { Text("Library") }
                    OutlinedButton(
                        onClick = haptics.onTap {
                            try {
                                picker.launch(arrayOf("application/pdf"))
                            } catch (exception: ActivityNotFoundException) {
                                Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canChoose,
                        modifier = Modifier.weight(1f)
                    ) { Text("Browse files") }
                }
            } else {
                Text(pdf.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Drag to reorder. Select pages to extract.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = haptics.onTap {
                            saveError = null
                            splitting = true
                        },
                        enabled = !busy && order.size > 1
                    ) { Text("Split PDF") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(order, key = { _, page -> page }) { index, page ->
                        val thumbnail by produceState<Bitmap?>(null, pdf.file, page) {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    library.pageThumbnail(pdf.file, page)
                                } catch (exception: IOException) {
                                    null
                                } catch (exception: RuntimeException) {
                                    null
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(76.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (dragging == page) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = page in selected,
                                    onCheckedChange = { checked ->
                                        selected = ArrayList(if (checked) selected + page else selected - page)
                                        haptics.toggle(checked)
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Select original page ${page + 1}" },
                                    enabled = !busy
                                )
                                Box(
                                    Modifier.size(width = 48.dp, height = 58.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    thumbnail?.let {
                                        Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize())
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                    if (index != page) {
                                        Text("Original ${page + 1}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                PdfReorderHandle(
                                    label = "Reorder original page ${page + 1}",
                                    index = index,
                                    lastIndex = order.lastIndex,
                                    enabled = !busy,
                                    rowStride = 84.dp,
                                    onDragActive = { dragging = if (it) page else null },
                                    onMove = { move(page, it) }
                                )
                                IconButton(
                                    onClick = haptics.onTap {
                                        order = ArrayList(order - page)
                                        selected = ArrayList(selected - page)
                                    },
                                    enabled = !busy && order.size > 1
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove original page ${page + 1}")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Copies contain page content. Forms and annotations are omitted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = haptics.onTap { nameOutput(pdf, PageExport.EXTRACTED) },
                        enabled = !busy && selected.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Extract (${selected.size})") }
                    Button(
                        onClick = haptics.onTap { nameOutput(pdf, PageExport.ORGANIZED) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save copy") }
                }
            }
        }
    }
}
