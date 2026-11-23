package com.nobi.scan.ui.reader

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobi.scan.PdfReaderActivity
import com.nobi.scan.data.DocumentLibrary
import com.nobi.scan.data.PdfReadingSession
import com.nobi.scan.ui.components.hapticOnFocus
import com.nobi.scan.ui.components.rememberNobiHaptics
import com.nobi.scan.ui.theme.LocalNobiPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

@Composable
internal fun PdfReaderScreen(source: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val library = remember(context) { DocumentLibrary(context.applicationContext) }
    val palette = LocalNobiPalette.current
    val haptics = rememberNobiHaptics()
    var session by remember(source) { mutableStateOf<PdfReadingSession?>(null) }
    var title by remember(source) { mutableStateOf("PDF reader") }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var pageIndex by rememberSaveable(source) { mutableIntStateOf(0) }
    var jumpOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        var opened: PdfReadingSession? = null
        try {
            withContext(Dispatchers.IO) {
                opened = PdfReadingSession.open(context, library, source)
            }
            val active = checkNotNull(opened)
            if (active.pageCount <= 0) {
                error = "This PDF has no pages."
                return@LaunchedEffect
            }
            title = withContext(Dispatchers.IO) { runCatching { library.pdfDisplayName(source) }.getOrDefault("PDF reader") }
            pageIndex = pageIndex.coerceIn(0, active.pageCount - 1)
            session = active
            awaitCancellation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            error = "This PDF is password-protected or Nobi Scan cannot access it."
        } catch (exception: Exception) {
            error = "Could not open this PDF."
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { opened?.close() }
        }
    }

    BackHandler(onBack = haptics.onTap(onClose))

    val document = session
    if (jumpOpen && document != null) {
        var entered by remember { mutableStateOf((pageIndex + 1).toString()) }
        val target = entered.toIntOrNull()
        AlertDialog(
            onDismissRequest = haptics.onTap { jumpOpen = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = entered, onValueChange = { entered = it },
                    modifier = Modifier.hapticOnFocus(haptics),
                    label = { Text("Page number (1–${document.pageCount})") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = target != null && target in 1..document.pageCount,
                    onClick = haptics.onTap {
                        pageIndex = checkNotNull(target) - 1
                        jumpOpen = false
                    }
                ) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = haptics.onTap { jumpOpen = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().background(palette.background).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = haptics.onTap(onClose)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close PDF")
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = haptics.onTap { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Reader options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = haptics.onTap { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Open in another app") }, onClick = haptics.onTap {
                        menuOpen = false
                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(source, "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val chooser = Intent.createChooser(view, null)
                            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, PdfReaderActivity::class.java)))
                        try {
                            context.startActivity(chooser)
                        } catch (exception: ActivityNotFoundException) {
                            Toast.makeText(context, "No other PDF app available", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            val message = error
            when {
                message != null -> Text(message, Modifier.padding(16.dp))
                document == null -> CircularProgressIndicator()
                else -> key(document, pageIndex) { ReaderPage(document, pageIndex) }
            }
        }

        if (document != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(enabled = pageIndex > 0, onClick = haptics.onTap { pageIndex-- }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                }
                TextButton(onClick = haptics.onTap { jumpOpen = true }) {
                    Text("Page ${pageIndex + 1} of ${document.pageCount}")
                }
                IconButton(enabled = pageIndex < document.pageCount - 1, onClick = haptics.onTap { pageIndex++ }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(document: PdfReadingSession, index: Int) {
    val haptics = rememberNobiHaptics()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(document, index) {
        try {
            bitmap = withContext(Dispatchers.IO) { document.render(index) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failed = true
        }
    }
    val current = bitmap
    when {
        failed -> Text("Could not render this page.")
        current == null -> CircularProgressIndicator()
        else -> {
            val image = remember(current) { current.asImageBitmap() }
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val fit = minOf(constraints.maxWidth.toFloat() / current.width, constraints.maxHeight.toFloat() / current.height)
                val imageWidth = current.width * fit
                val imageHeight = current.height * fit
                val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
                fun transformAt(nextZoom: Float, centroid: Offset, delta: Offset) {
                    val focus = centroid.takeIf { it != Offset.Unspecified } ?: center
                    val moved = (pan + center - focus) * (nextZoom / zoom) + focus - center + delta
                    val maxX = ((imageWidth * nextZoom - constraints.maxWidth) / 2).coerceAtLeast(0f)
                    val maxY = ((imageHeight * nextZoom - constraints.maxHeight) / 2).coerceAtLeast(0f)
                    pan = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                    zoom = nextZoom
                }
                val transform = rememberTransformableState { centroid, change, offset, _ ->
                    transformAt((zoom * change).coerceIn(1f, 4f), centroid, offset)
                }
                LaunchedEffect(transform.isTransformInProgress) {
                    if (transform.isTransformInProgress) haptics.grab()
                }
                Image(
                    image, contentDescription = "PDF page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(center) {
                            detectTapGestures(onDoubleTap = { point ->
                                haptics.step()
                                transformAt(if (zoom == 1f) 2.5f else 1f, point, Offset.Zero)
                            })
                        }
                        .transformable(transform)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                )
            }
        }
    }
}
