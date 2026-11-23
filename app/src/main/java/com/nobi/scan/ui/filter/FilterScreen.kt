package com.nobi.scan.ui.filter

import com.nobi.scan.ui.components.rememberNobiHaptics
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.nobi.scan.data.DraftPage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobi.scan.cv.DocumentFilter
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.theme.LocalNobiPalette
import com.nobi.scan.ui.components.LiquidPurplePillButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FilterScreen(
    rawBitmap: Bitmap,
    pages: List<DraftPage>,
    selectedIndex: Int,
    saving: Boolean,
    ocrLanguage: OcrLanguage,
    onOcrLanguageChange: (OcrLanguage) -> Unit,
    onSelectPage: (Int) -> Unit,
    onMovePage: (Int) -> Unit,
    onFilterChange: (DocumentFilter) -> Unit,
    onRotate: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    onCrop: () -> Unit,
    onKeepScanning: () -> Unit,
    onSaveDocument: () -> Unit,
) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val page = pages[selectedIndex]
    val filter = page.filter
    fun selectPage(index: Int) {
        if (saving || index == selectedIndex) return
        haptics.step()
        onSelectPage(index)
    }

    val thumbnails = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        val layout = thumbnails.layoutInfo
        val visible = layout.visibleItemsInfo.any {
            it.index == selectedIndex && it.offset >= layout.viewportStartOffset &&
                it.offset + it.size <= layout.viewportEndOffset
        }
        if (!visible) thumbnails.scrollToItem(selectedIndex)
    }
    var filtersOpen by remember { mutableStateOf(false) }
    var processedBitmap by remember(rawBitmap, page) { mutableStateOf<Bitmap?>(null) }
    var processing by remember(rawBitmap, page) { mutableStateOf(true) }

    LaunchedEffect(rawBitmap, page) {
        processedBitmap = try {
            withContext(Dispatchers.Default) { page.render(rawBitmap) }
        } catch (invalid: IllegalArgumentException) {
            null
        }
        processing = false
    }

    val canSave = !saving && !processing && processedBitmap != null
    var ocrOpen by remember(page) { mutableStateOf(false) }
    var detectionOpen by remember(page) { mutableStateOf(false) }
    val correctedPage = processedBitmap
    if (detectionOpen && correctedPage != null) {
        DetectedTextScreen(
            page = correctedPage,
            language = ocrLanguage,
            onLanguageChanged = onOcrLanguageChange,
            onClose = { detectionOpen = false }
        )
        return
    }
    if (ocrOpen && correctedPage != null) {
        OcrDialog(
            page = correctedPage,
            language = ocrLanguage,
            onLanguageChanged = onOcrLanguageChange,
            onDismiss = { ocrOpen = false }
        )
    }
    val outlineColors = ButtonDefaults.outlinedButtonColors(
        contentColor = palette.accent,
        disabledContentColor = palette.mutedInk.copy(alpha = .5f)
    )
    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val reviewHeight = maxHeight.coerceAtLeast(448.dp)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(reviewHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = haptics.onTap(onKeepScanning), enabled = !saving) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to scanner", tint = palette.ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Review scan", style = MaterialTheme.typography.titleLarge, color = palette.ink)
                            Text("Page ${selectedIndex + 1} of ${pages.size}",
                                style = MaterialTheme.typography.bodyMedium, color = palette.mutedInk)
                        }
                    }

                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PageStackPreview(
                            bitmap = processedBitmap,
                            pageKey = page.source.path,
                            selectedIndex = selectedIndex,
                            pageCount = pages.size,
                            enabled = !saving && processedBitmap != null,
                            onSelectPage = ::selectPage,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (processing) {
                            CircularProgressIndicator(color = palette.accent)
                        } else if (processedBitmap == null) {
                            Text("Adjust the crop corners and try again.", color = palette.ink)
                        }
                    }

                    val trayShape = RoundedCornerShape(28.dp)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(palette.lens.copy(alpha = .85f), palette.surface.copy(alpha = .9f))),
                                trayShape
                            )
                            .border(1.dp, palette.rim, trayShape)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (pages.size > 1) {
                                FilledTonalIconButton(
                                    onClick = { haptics.step(); onMovePage(-1) },
                                    enabled = !saving && selectedIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Move page earlier")
                                }
                            }
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                state = thumbnails,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                itemsIndexed(pages, key = { _, item -> item.source.path }) { index, item ->
                                    val thumbnail by produceState<Bitmap?>(null, item) {
                                        value = null
                                        value = withContext(Dispatchers.IO) {
                                            try {
                                                item.renderThumbnail()
                                            } catch (exception: IllegalStateException) {
                                                null
                                            }
                                        }
                                    }
                                    val selected = index == selectedIndex
                                    val shape = RoundedCornerShape(10.dp)
                                    Column(
                                        Modifier.width(62.dp).clip(shape)
                                            .background(palette.surface.copy(alpha = .72f))
                                            .border(2.dp, if (selected) palette.accent else Color.Transparent, shape)
                                            .selectable(selected, enabled = !saving, role = Role.Tab) { selectPage(index) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(Modifier.height(60.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            thumbnail?.let { image ->
                                                Image(remember(image) { image.asImageBitmap() }, null,
                                                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                            }
                                        }
                                        Text(
                                            "Page ${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) palette.accent else palette.ink
                                        )
                                    }
                                }
                            }
                            if (pages.size > 1) {
                                FilledTonalIconButton(
                                    onClick = { haptics.step(); onMovePage(1) },
                                    enabled = !saving && selectedIndex < pages.lastIndex
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Move page later")
                                }
                            }
                        }
                        val toolColors = ButtonDefaults.textButtonColors(
                            contentColor = palette.ink,
                            disabledContentColor = palette.mutedInk.copy(alpha = .5f)
                        )
                        Row(
                            Modifier.fillMaxWidth().background(palette.surface.copy(alpha = .64f), RoundedCornerShape(24.dp)),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = haptics.onTap(onRetake), enabled = !saving, colors = toolColors, contentPadding = PaddingValues(4.dp)) {
                                ReviewToolLabel(Icons.Default.CameraAlt, "Rescan")
                            }
                            TextButton(modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = haptics.onTap(onCrop), enabled = !saving, colors = toolColors, contentPadding = PaddingValues(4.dp)) {
                                ReviewToolLabel(Icons.Default.Crop, "Crop")
                            }
                            TextButton(modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = haptics.onTap(onRotate), enabled = !saving, colors = toolColors, contentPadding = PaddingValues(4.dp)) {
                                ReviewToolLabel(Icons.AutoMirrored.Filled.RotateRight, "Rotate")
                            }
                            Box(Modifier.weight(1f)) {
                                TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(4.dp), onClick = haptics.onTap { filtersOpen = true }, enabled = !saving, colors = toolColors) {
                                    ReviewToolLabel(Icons.Default.FilterList, "Filters")
                                }
                                DropdownMenu(expanded = filtersOpen, onDismissRequest = haptics.onTap { filtersOpen = false }) {
                                    DocumentFilter.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            trailingIcon = if (option == filter) {
                                                { Icon(Icons.Default.Check, "Selected") }
                                            } else null,
                                            onClick = {
                                                if (option != filter) {
                                                    haptics.step()
                                                    onFilterChange(option)
                                                }
                                                filtersOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            TextButton(modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = haptics.onTap(onDelete), enabled = !saving, colors = toolColors, contentPadding = PaddingValues(4.dp)) {
                                ReviewToolLabel(Icons.Default.DeleteOutline, "Delete")
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(4.dp),
                                enabled = canSave,
                                onClick = haptics.onTap { detectionOpen = true }
                            ) {
                                Text("Detect text", textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(4.dp),
                                enabled = canSave,
                                onClick = haptics.onTap { ocrOpen = true }
                            ) {
                                Text("Recognize text", textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = outlineColors,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    enabled = !saving,
                    onClick = haptics.onTap(onKeepScanning)
                ) { Text("Keep scanning", textAlign = TextAlign.Center) }
                LiquidPurplePillButton(
                    text = if (saving) "Saving…" else "Save PDF",
                    modifier = Modifier.weight(1f),
                    onClick = haptics.onTap(onSaveDocument),
                    enabled = canSave
                )
            }
        }
    }
}

@Composable
private fun ReviewToolLabel(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
