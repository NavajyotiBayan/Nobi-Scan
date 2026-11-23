package com.nobi.scan.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nobi.scan.cv.DocumentFilter
import com.nobi.scan.data.AppAppearance
import com.nobi.scan.data.ScanPreferences
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.update.UpdateControls
import com.nobi.scan.ui.components.AuraBackgroundBox
import com.nobi.scan.ui.components.rememberNobiHaptics
import com.nobi.scan.ui.theme.LocalNobiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private enum class SettingsDialog { FILTER, LANGUAGE, STORAGE, LICENSES, RESET }

@Composable
internal fun SettingsScreen(
    preferences: ScanPreferences, onChange: (ScanPreferences) -> Unit, updates: UpdateControls?
) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val close = haptics.onTap { dialog = null }

    AuraBackgroundBox {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = palette.ink)
            SettingsCard("Appearance") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AppAppearance.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.appearance == mode,
                            onClick = {
                                if (mode != preferences.appearance) {
                                    haptics.step()
                                    onChange(preferences.copy(appearance = mode))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, AppAppearance.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = palette.accent, activeContentColor = palette.onAccent,
                                inactiveContainerColor = palette.surface, inactiveContentColor = palette.ink
                            ),
                            icon = {}
                        ) { Text(mode.label) }
                    }
                }
            }
            SettingsCard("Scan & PDF defaults") {
                SettingsLink("Default scan filter", value = preferences.filter.label) { dialog = SettingsDialog.FILTER }
                SettingsDivider()
                SettingsLink("OCR language", value = preferences.language.label) { dialog = SettingsDialog.LANGUAGE }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(preferences.searchable, role = Role.Switch) {
                            haptics.toggle(it)
                            onChange(preferences.copy(searchable = it))
                        }.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Searchable PDF by default", Modifier.weight(1f), color = palette.ink)
                    Switch(checked = preferences.searchable, onCheckedChange = null)
                }
            }
            SettingsCard("Storage & privacy") {
                SettingsLink("Local storage", description = "Documents are stored on this device.") {
                    dialog = SettingsDialog.STORAGE
                }
            }
            SettingsCard("About") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Version", Modifier.weight(1f), color = palette.ink)
                    Text(version, color = palette.mutedInk)
                }
                SettingsDivider()
                if (updates != null) {
                    SettingsLink(
                        "Check for updates",
                        description = if (updates.available == null) null else "Update available"
                    ) { updates.check(manual = true) }
                    SettingsDivider()
                }
                SettingsLink("Open-source licenses") { dialog = SettingsDialog.LICENSES }
                SettingsDivider()
                SettingsLink("More tools", description = "@NavajyotiBayan", external = true) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NavajyotiBayan"))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            TextButton(
                onClick = haptics.onTap { dialog = SettingsDialog.RESET },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Reset preferences", color = palette.accent) }
        }
    }

    when (dialog) {
        SettingsDialog.FILTER -> ChoiceDialog("Default scan filter", DocumentFilter.entries, preferences.filter,
            DocumentFilter::label, close) { onChange(preferences.copy(filter = it)); dialog = null }
        SettingsDialog.LANGUAGE -> ChoiceDialog("OCR language", OcrLanguage.entries, preferences.language,
            OcrLanguage::label, close) { onChange(preferences.copy(language = it)); dialog = null }
        SettingsDialog.STORAGE -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Local storage") },
            text = {
                Text(
                    "Scans and text recognition are processed on this device. Your library PDFs are saved in " +
                        "the app’s private Nobi Scan/Documents folder. Clearing app data or uninstalling " +
                        "Nobi Scan removes this library.\n\nUse Export a copy from a document’s menu to save " +
                        "a separate file at a location you choose. Exported copies stay there until you remove them."
                )
            },
            confirmButton = { TextButton(onClick = close) { Text("Close") } }
        )
        SettingsDialog.LICENSES -> LicensesDialog(close)
        SettingsDialog.RESET -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Reset preferences?") },
            text = { Text("Restore appearance, scan filter, OCR language, and PDF export defaults. Your saved PDFs will be kept.") },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    onChange(ScanPreferences())
                    dialog = null
                    haptics.confirm()
                }) { Text("Reset") }
            }
        )
        null -> Unit
    }
}

@Composable
private fun SettingsCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalNobiPalette.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(palette.lens.copy(alpha = .65f), palette.surface.copy(alpha = .86f))), shape)
            .border(1.dp, palette.rim, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, Modifier.padding(start = 4.dp, bottom = 3.dp), color = palette.ink,
            style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(color = LocalNobiPalette.current.mutedInk.copy(alpha = .22f))

@Composable
private fun SettingsLink(
    label: String,
    description: String? = null,
    value: String? = null,
    external: Boolean = false,
    action: () -> Unit
) {
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = haptics.onTap(action))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = palette.ink)
            if (description != null) Text(description, color = palette.mutedInk,
                style = MaterialTheme.typography.bodySmall)
        }
        if (value != null) Text(value, color = palette.mutedInk)
        Icon(
            if (external) Icons.Default.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (external) "Opens externally" else null,
            tint = palette.mutedInk,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String, options: List<T>, selected: T, label: (T) -> String,
    onClose: () -> Unit, onSelect: (T) -> Unit
) {
    val haptics = rememberNobiHaptics()
    AlertDialog(
        onDismissRequest = onClose, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(option == selected, role = Role.RadioButton) {
                                haptics.step()
                                onSelect(option)
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

private val licenseNotices = listOf(
    "FairScan" to "FairScan-GPL-3.0.txt",
    "PDFBox-Android license" to "PdfBox-Android-LICENSE.txt",
    "PDFBox-Android notice" to "PdfBox-Android-NOTICE.txt",
    "Noto fonts" to "Noto-fonts-copyright.txt",
    "Tesseract" to "ocr/Tesseract-LICENSE.txt",
    "Tesseract4Android" to "ocr/Tesseract4Android-LICENSE.txt",
    "Tesseract language data" to "ocr/tessdata-fast-LICENSE.txt",
    "Leptonica" to "ocr/Leptonica-LICENSE.txt",
    "libpng" to "ocr/libpng-LICENSE.txt",
    "IJG libjpeg" to "ocr/IJG-libjpeg-README.txt"
)

@Composable
private fun LicensesDialog(onClose: () -> Unit) {
    val haptics = rememberNobiHaptics()
    val assets = LocalContext.current.assets
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val notice = selected?.let(licenseNotices::get)
    val closeNotice = haptics.onTap { selected = null }
    val content by produceState<String?>(null, notice, assets) {
        value = null
        if (notice != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    assets.open("notices/${notice.second}").bufferedReader().use { it.readText() }
                } catch (_: IOException) {
                    "Could not load this notice."
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = if (notice == null) onClose else closeNotice,
        title = { Text(notice?.first ?: "Open-source licenses") },
        text = {
            key(notice) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (notice == null) {
                        licenseNotices.forEachIndexed { index, item ->
                            SettingsLink(item.first) { selected = index }
                        }
                    } else {
                        Text(content ?: "Loading…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (notice == null) onClose else closeNotice) {
                Text(if (notice == null) "Close" else "Back")
            }
        }
    )
}
