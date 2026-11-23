package com.nobi.scan.ui.tools

import com.nobi.scan.ui.components.rememberNobiHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobi.scan.ui.components.AuraBackgroundBox

@Composable
fun ToolsScreen(onMergePdfs: () -> Unit, onOrganizePdf: () -> Unit, onCompressPdf: () -> Unit) {
    val haptics = rememberNobiHaptics()
    AuraBackgroundBox {
        Column(
            modifier = Modifier.statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tools", style = MaterialTheme.typography.headlineMedium)
            Card(onClick = haptics.onTap(onMergePdfs), modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Merge PDFs") },
                    supportingContent = { Text("Combine PDFs in the order you choose") },
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                )
            }
            Card(onClick = haptics.onTap(onOrganizePdf), modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Organize PDF") },
                    supportingContent = { Text("Reorder, remove or extract pages") },
                    leadingContent = { Icon(Icons.Default.Reorder, contentDescription = null) }
                )
            }
            Card(onClick = haptics.onTap(onCompressPdf), modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Compress PDF") },
                    supportingContent = { Text("Reduce the size of a PDF") },
                    leadingContent = { Icon(Icons.Default.Compress, contentDescription = null) }
                )
            }
        }
    }
}
