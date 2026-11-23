package com.nobi.scan.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nobi.scan.ocr.OcrLanguage
import com.nobi.scan.ui.components.rememberNobiHaptics

@Composable
internal fun SearchablePdfOptions(
    checked: Boolean,
    language: OcrLanguage,
    busy: Boolean,
    readingPage: Int?,
    pageCount: Int,
    onChecked: (Boolean) -> Unit,
    onLanguage: (OcrLanguage) -> Unit
) {
    val haptics = rememberNobiHaptics()
    var languagesOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(checked, enabled = !busy, role = Role.Checkbox) {
                haptics.toggle(it)
                onChecked(it)
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
            Text("Searchable PDF")
        }
        if (checked) {
            Box {
                OutlinedButton(
                    enabled = !busy, onClick = haptics.onTap { languagesOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(language.label, Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose OCR language")
                }
                DropdownMenu(expanded = languagesOpen, onDismissRequest = haptics.onTap { languagesOpen = false }) {
                    OcrLanguage.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            if (language != option) {
                                haptics.step()
                                onLanguage(option)
                            }
                            languagesOpen = false
                        })
                    }
                }
            }
            if (busy) {
                Text(
                    readingPage?.let { "Reading page $it of $pageCount…" } ?: "Preparing recognition…",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("Use Find in your PDF viewer to search the scan.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
