package com.nobi.scan.ui.home

import com.nobi.scan.ui.components.hapticOnFocus
import com.nobi.scan.ui.components.rememberNobiHaptics
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.nobi.scan.R
import com.nobi.scan.PdfReaderActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.LiquidPurplePillButton
import com.nobi.scan.ui.components.PdfNameDialog
import com.nobi.scan.ui.theme.LocalNobiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    library: DocumentLibrary,
    onToggleTheme: () -> Unit,
    onEditPdf: (File) -> Unit
) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenWidthDp < 320
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    val documents by produceState(emptyList<File>(), library, refresh) {
        value = withContext(Dispatchers.IO) { library.list() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = exportPath?.let(::File)
        exportPath = null
        if (uri != null && file != null) scope.launch {
            val message = try {
                withContext(Dispatchers.IO) { library.export(file, uri) }
                haptics.confirm()
                "PDF exported"
            } catch (exception: java.io.IOException) {
                "Could not export PDF"
            } catch (exception: SecurityException) {
                "Could not access export location"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val visible = documents.filter { it.name.contains(query.trim(), ignoreCase = true) }

    renameTarget?.let { file ->
        PdfNameDialog(
            title = "Rename PDF",
            name = renameText,
            onNameChange = {
                renameText = it
                renameError = null
            },
            busy = renaming,
            error = renameError,
            confirmLabel = "Rename",
            onConfirm = {
                if (!renaming) {
                    renaming = true
                    scope.launch {
                        try {
                            val error = withContext(Dispatchers.IO) { library.rename(file, renameText) }
                            if (error == null) {
                                haptics.confirm()
                                refresh++
                                renameTarget = null
                            } else renameError = error
                        } finally {
                            renaming = false
                        }
                    }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = haptics.onTapIfEnabled(!deleting) { deleteTarget = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Delete PDF?") },
            text = { Text("Delete ${file.name} from your Nobi Scan library?") },
            dismissButton = {
                TextButton(onClick = haptics.onTap { deleteTarget = null }, enabled = !deleting) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = haptics.onTap {
                        deleting = true
                        scope.launch {
                            try {
                                val removed = withContext(Dispatchers.IO) { library.delete(file) }
                                if (removed) {
                                    haptics.confirm()
                                    refresh++
                                    deleteTarget = null
                                }
                                Toast.makeText(
                                    context,
                                    if (removed) "PDF deleted" else "Could not delete PDF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                deleting = false
                            }
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.nobi_scan_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                )
                Text("Nobi Scan", style = if (compact) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = palette.ink,
                    modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier.background(palette.glass, RoundedCornerShape(50)),
                    onClick = {
                        haptics.toggle(!palette.isDark)
                        onToggleTheme()
                    }
                ) {
                    Icon(
                        if (palette.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (palette.isDark) "Switch to light theme" else "Switch to dark theme",
                        tint = palette.ink
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).hapticOnFocus(haptics),
                singleLine = true,
                label = { Text("Search documents") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.ink, unfocusedTextColor = palette.ink,
                    focusedLabelColor = palette.ink, unfocusedLabelColor = palette.mutedInk,
                    unfocusedBorderColor = palette.rim,
                    focusedBorderColor = palette.accent,
                    focusedLeadingIconColor = palette.accent,
                    unfocusedLeadingIconColor = palette.mutedInk,
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface
                )
            )
            Text(
                if (query.isBlank()) "Recent scans" else "Search results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (compact) 1 else 2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 152.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (query.isBlank()) "Your saved scans will appear here." else "No matching documents.",
                        color = palette.mutedInk, modifier = Modifier.padding(vertical = 12.dp))
                }
                items(visible, key = { it.path }) { file ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val uri = remember(file) { library.uri(file) }
                    val modified = file.lastModified()
                    val preview by produceState<DocumentLibrary.Preview?>(null, file, modified) {
                        value = withContext(Dispatchers.IO) { library.preview(file) }
                    }
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(shape).background(palette.surface)
                            .border(1.dp, palette.rim, shape).combinedClickable(
                                onLongClickLabel = "PDF actions",
                                onLongClick = { menuOpen = true },
                                onClick = {
                                    haptics.tap()
                                    context.startActivity(PdfReaderActivity.openIntent(context, uri))
                                }
                            )
                    ) {
                        val thumbnailShape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp)
                                .aspectRatio(0.88f).clip(thumbnailShape).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumbnail = preview?.thumbnail
                            if (thumbnail == null) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = palette.accent)
                            } else {
                                Image(thumbnail.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize())
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    file.nameWithoutExtension, color = palette.ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                val size = Formatter.formatFileSize(context, file.length())
                                val metadata = when (val pageCount = preview?.pages) {
                                    null -> size
                                    1 -> "1 page · $size"
                                    else -> "$pageCount pages · $size"
                                }
                                Text(metadata, style = MaterialTheme.typography.bodySmall, color = palette.mutedInk,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box {
                                IconButton(onClick = haptics.onTap { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, "PDF options", tint = palette.ink)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = haptics.onTap { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Export a copy") }, onClick = {
                                        haptics.tap()
                                        menuOpen = false
                                        exportPath = file.path
                                        export.launch(file.name)
                                    })
                                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                                        haptics.tap()
                                        menuOpen = false
                                        context.startActivity(library.shareIntent(file))
                                    })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                        haptics.tap()
                                        menuOpen = false
                                        renameText = file.nameWithoutExtension
                                        renameError = null
                                        renameTarget = file
                                    })
                                    DropdownMenuItem(text = { Text("Edit copy") }, onClick = {
                                        haptics.tap()
                                        menuOpen = false
                                        onEditPdf(file)
                                    })
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteOutline, null,
                                                tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            haptics.tap()
                                            menuOpen = false
                                            deleteTarget = file
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeScanButton(onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val scope = rememberCoroutineScope()
    var openingScanner by remember { mutableStateOf(false) }
    LiquidPurplePillButton(
        text = "Scan document",
        icon = Icons.Default.DocumentScanner,
        modifier = modifier.widthIn(min = 218.dp).shadow(
            elevation = 7.dp,
            shape = RoundedCornerShape(50),
            ambientColor = palette.accent,
            spotColor = palette.accent
        ),
        framedIcon = true,
        enabled = !openingScanner,
        onClick = {
            if (!openingScanner) {
                openingScanner = true
                haptics.tap()
                scope.launch {
                    delay(155)
                    onOpenScan()
                }
            }
        }
    )
}
