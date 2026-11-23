package com.nobi.scan.ui.tools

import com.nobi.scan.ui.components.rememberNobiHaptics
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.PdfNameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
internal fun MergePdfScreen(library: DocumentLibrary, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberNobiHaptics()
    val scope = rememberCoroutineScope()
    var sources by rememberSaveable { mutableStateOf(arrayListOf<Uri>()) }
    var selectingLibrary by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Merged PDF") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        sources = ArrayList((sources + selected).distinct())
    }
    BackHandler(onBack = haptics.onTapIfEnabled(!busy, onClose))

    fun move(source: Uri, direction: Int): Boolean {
        if (busy) return false
        val from = sources.indexOf(source)
        val to = from + direction
        if (from < 0 || to !in sources.indices) return false
        sources = ArrayList(sources).apply { add(to, removeAt(from)) }
        haptics.step()
        return true
    }

    fun save(share: Boolean) {
        if (busy || sources.size < 2) return
        error = library.nameError(name)
        if (error != null) return
        busy = true
        val orderedSources = sources.toList()
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) { library.mergePdfs(orderedSources, name) }
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
            } catch (exception: IOException) {
                error = exception.message ?: "Could not save the merged PDF."
            } catch (exception: IllegalArgumentException) {
                error = "One of the selected PDFs could not be read."
            } finally {
                busy = false
            }
        }
    }

    if (naming) {
        PdfNameDialog(
            title = if (busy) "Merging PDFs…" else "Name merged PDF",
            name = name,
            onNameChange = { name = it; error = null },
            busy = busy,
            error = error,
            confirmLabel = "Save PDF",
            onConfirm = { save(share = false) },
            onShare = { save(share = true) },
            onDismiss = { if (!busy) naming = false }
        )
    }

    if (selectingLibrary) {
        LibraryPdfPicker(
            library = library,
            included = sources.toSet(),
            onAdd = { selected -> sources = ArrayList((sources + selected).distinct()) },
            onDismiss = { selectingLibrary = false }
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
                Text("Merge PDFs", style = MaterialTheme.typography.headlineSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = haptics.onTap { selectingLibrary = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Library")
                }
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        try {
                            picker.launch(arrayOf("application/pdf"))
                        } catch (exception: ActivityNotFoundException) {
                            Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Browse files") }
            }
            Text(
                if (sources.isEmpty()) "Add at least two PDFs." else "Drag handles to change the document order.",
                style = MaterialTheme.typography.bodyMedium
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(sources, key = { _, uri -> uri.toString() }) { index, uri ->
                    val filename by produceState("PDF", uri) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "PDF"
                            } catch (exception: SecurityException) {
                                "Unavailable PDF"
                            } catch (exception: IllegalArgumentException) {
                                "PDF"
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(68.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = if (dragging == uri) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                            Text(
                                filename,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            PdfReorderHandle(
                                label = "Reorder $filename",
                                index = index,
                                lastIndex = sources.lastIndex,
                                enabled = !busy,
                                rowStride = 76.dp,
                                onDragActive = { dragging = if (it) uri else null },
                                onMove = { move(uri, it) }
                            )
                            IconButton(onClick = haptics.onTap { sources = ArrayList(sources - uri) }, enabled = !busy) {
                                Icon(Icons.Default.Close, contentDescription = "Remove $filename")
                            }
                        }
                    }
                }
            }
            Button(
                onClick = haptics.onTap {
                    error = null
                    naming = true
                },
                enabled = !busy && sources.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) { Text("Merge PDFs") }
        }
    }
}

@Composable
internal fun LibraryPdfPicker(
    library: DocumentLibrary,
    included: Set<Uri>,
    onAdd: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    singleSelection: Boolean = false
) {
    val files by produceState<List<File>?>(null, library) {
        value = withContext(Dispatchers.IO) { library.list() }
    }
    val haptics = rememberNobiHaptics()
    val selected = remember { mutableStateListOf<Uri>() }
    AlertDialog(
        onDismissRequest = haptics.onTap(onDismiss),
        title = { Text(if (singleSelection) "Choose a PDF" else "Add from library") },
        text = {
            when {
                files == null -> CircularProgressIndicator()
                files.orEmpty().isEmpty() -> Text("No PDFs in your library yet.")
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(files.orEmpty(), key = { it.path }) { file ->
                        val uri = remember(file) { library.uri(file) }
                        val added = uri in included
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !added) {
                                if (singleSelection) selected.removeAll { it != uri }
                                val removed = selected.remove(uri)
                                if (!removed) selected.add(uri)
                                haptics.toggle(!removed)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = added || uri in selected, onCheckedChange = null, enabled = !added)
                            Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = haptics.onTap {
                onAdd(selected.toList())
                onDismiss()
            }, enabled = selected.isNotEmpty()) {
                Text(if (singleSelection) "Open PDF" else "Add PDFs")
            }
        },
        dismissButton = { TextButton(onClick = haptics.onTap(onDismiss)) { Text("Cancel") } }
    )
}
