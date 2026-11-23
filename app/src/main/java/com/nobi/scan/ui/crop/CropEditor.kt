package com.nobi.scan.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nobi.scan.cv.DocumentProcessor
import com.nobi.scan.data.DraftPage
import com.nobi.scan.data.PageTrim
import com.nobi.scan.data.TrimRatio
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.LiquidPurplePillButton
import com.nobi.scan.ui.components.rememberNobiHaptics
import com.nobi.scan.ui.theme.LocalNobiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat

private enum class CropMode(val label: String) { EDGES("Edges"), TRIM("Trim") }

@Composable
internal fun CropEditor(bitmap: Bitmap, page: DraftPage, onBack: () -> Unit, onApply: (DraftPage) -> Unit) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val edges = remember(page) { CropScreenState(page.quad) }
    var draft by remember(page) { mutableStateOf(page) }
    var mode by remember(page) { mutableStateOf(CropMode.EDGES) }
    var trimViewportReset by remember(page) { mutableIntStateOf(0) }
    val trimming = mode == CropMode.TRIM
    val quad = edges.currentQuad
    val turns = draft.turns
    val preview = if (trimming) {
        key(bitmap, quad, draft.filter, turns) {
            val result by produceState<Result<Bitmap>?>(null) {
                value = withContext(Dispatchers.Default) {
                    try {
                        Result.success(DocumentProcessor.rectify(bitmap, quad, draft.filter, turns))
                    } catch (invalid: IllegalArgumentException) {
                        Result.failure(invalid)
                    }
                }
            }
            result
        }
    } else null
    val image = preview?.getOrNull()

    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = haptics.onTap(onBack)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to review", tint = palette.ink)
                }
                Text("Crop", Modifier.weight(1f), color = palette.ink, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = haptics.onTap {
                    if (trimming) {
                        draft = draft.copy(turns = 0, trim = PageTrim())
                        trimViewportReset++
                    } else {
                        edges.reset(page.quad)
                    }
                }) { Text("Reset", color = palette.accent) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val editorHeight = maxHeight.coerceAtLeast(if (trimming) 420.dp else 320.dp)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(editorHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                        if (!trimming) {
                            CropEdgesViewport(bitmap, edges)
                        } else if (image != null) {
                            key(trimViewportReset) {
                                TrimViewport(image, draft.trim, palette.accent) { region ->
                                    draft = draft.copy(trim = draft.trim.copy(region = region))
                                }
                            }
                        } else if (preview == null) {
                            CircularProgressIndicator(color = palette.accent)
                        } else {
                            Text("Adjust the document edges and try again.", color = palette.ink)
                        }
                    }
                    val trayShape = RoundedCornerShape(24.dp)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(palette.lens, palette.surface)), trayShape)
                            .border(1.dp, palette.rim, trayShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            CropMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = option == mode,
                                    onClick = {
                                        if (option != mode) {
                                            haptics.step()
                                            mode = option
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, CropMode.entries.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = palette.accent,
                                        activeContentColor = palette.onAccent,
                                        inactiveContainerColor = palette.surface,
                                        inactiveContentColor = palette.ink
                                    )
                                ) { Text(option.label) }
                            }
                        }
                        if (trimming) {
                            TrimControls(
                                trim = draft.trim,
                                image = image,
                                onRotate = { draft = draft.rotateRight() },
                                onChange = { draft = draft.copy(trim = it) }
                            )
                        } else {
                            Text(
                                "Drag the corners to fit the page.",
                                Modifier.padding(vertical = 12.dp),
                                color = palette.mutedInk,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            LiquidPurplePillButton(
                text = "Apply crop",
                modifier = Modifier.fillMaxWidth(),
                enabled = !trimming || image != null,
                onClick = {
                    haptics.confirm()
                    onApply(draft.copy(quad = edges.currentQuad))
                }
            )
        }
    }
}

@Composable
private fun TrimControls(trim: PageTrim, image: Bitmap?, onRotate: () -> Unit, onChange: (PageTrim) -> Unit) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val angleFormat = remember { NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(enabled = image != null, onClick = haptics.onTap(onRotate)) {
            Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate 90°")
        }
        TrimRatio.entries.forEach { option ->
            FilterChip(
                selected = trim.ratio == option,
                enabled = image != null,
                onClick = {
                    if (image != null && option != trim.ratio) {
                        val ratio = option.normalized(image.width, image.height)
                        val region = ratio?.let(trim.region::fitRatio) ?: trim.region
                        haptics.step()
                        onChange(trim.copy(region = region, ratio = option))
                    }
                },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = palette.ink,
                    selectedLabelColor = palette.onAccent,
                    selectedContainerColor = palette.accent
                )
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Straighten", Modifier.weight(1f), color = palette.ink, style = MaterialTheme.typography.labelLarge)
        Text("${angleFormat.format(trim.degrees)}°", color = palette.ink, style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = trim.degrees,
        valueRange = -15f..15f,
        enabled = image != null,
        onValueChange = { onChange(trim.copy(degrees = it)) },
        onValueChangeFinished = { haptics.step() },
        modifier = Modifier.semantics { contentDescription = "Straighten angle" },
        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
    )
}
