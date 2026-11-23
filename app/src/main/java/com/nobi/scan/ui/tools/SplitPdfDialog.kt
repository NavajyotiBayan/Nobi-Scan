package com.nobi.scan.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.ui.components.hapticOnFocus
import com.nobi.scan.ui.components.rememberNobiHaptics

private val pageSpan = Regex("""\s*([0-9]+)\s*(?:[-–—]\s*([0-9]+)\s*)?""")

internal fun parseSplitRanges(input: String, pageCount: Int): List<IntRange> {
    val parts = input.split(',')
    require(parts.size >= 2) { "Enter at least two parts separated by commas." }
    return parts.mapIndexed { index, part ->
        val position = index + 1
        val match = pageSpan.matchEntire(part)
        require(match != null) { "Part $position needs a page or range, such as 3 or 4-6." }
        val first = match.groupValues[1].toIntOrNull()
        val last = match.groups[2]?.value?.toIntOrNull() ?: first.takeIf { match.groups[2] == null }
        require(first != null && last != null && first in 1..pageCount && last in first..pageCount) {
            "Part $position must be in ascending order and within pages 1–$pageCount."
        }
        first..last
    }
}

@Composable
internal fun SplitPdfDialog(
    library: DocumentLibrary,
    filename: String,
    pageCount: Int,
    busy: Boolean,
    saveError: String?,
    onSave: (List<IntRange>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberNobiHaptics()
    var rangesText by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable {
        mutableStateOf(filename.substringBeforeLast('.', filename).take(48))
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    val parsed = remember(rangesText, pageCount) { runCatching { parseSplitRanges(rangesText, pageCount) } }
    val ranges = parsed.getOrNull()
    AlertDialog(
        onDismissRequest = haptics.onTapIfEnabled(!busy, onDismiss),
        title = { Text("Split PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it },
                    modifier = Modifier.fillMaxWidth().hapticOnFocus(haptics),
                    label = { Text("Page ranges") },
                    placeholder = { Text(if (pageCount >= 3) "1-2, 3" else "1, 2") },
                    supportingText = {
                        Text(parsed.exceptionOrNull()?.message?.takeIf { rangesText.isNotBlank() }
                            ?: "Use commas between parts. Page numbers follow the current order.")
                    },
                    isError = rangesText.isNotBlank() && ranges == null,
                    enabled = !busy,
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    modifier = Modifier.fillMaxWidth().hapticOnFocus(haptics),
                    label = { Text("Base filename") },
                    supportingText = nameError?.let { message -> { Text(message) } },
                    isError = nameError != null,
                    enabled = !busy,
                    singleLine = true
                )
                if (ranges != null) {
                    LazyColumn(Modifier.heightIn(max = 128.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(ranges) { index, range ->
                            val pages = if (range.first == range.last) "Page ${range.first}"
                                else "Pages ${range.first}–${range.last}"
                            Text(
                                "$pages · ${library.splitPartName(name, index + 1)}.pdf",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = ranges != null && !busy, onClick = haptics.onTap {
                if (ranges != null) {
                    nameError = library.nameError(name) ?: library.nameError(library.splitPartName(name, ranges.size))
                    if (nameError == null) onSave(ranges, name)
                }
            }) { Text("Save parts") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = haptics.onTap(onDismiss)) { Text("Cancel") }
        }
    )
}
