package com.nobi.scan.ui.camera

import com.nobi.scan.ui.components.rememberNobiHaptics
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.nobi.scan.cv.detectStillDocument
import com.nobi.scan.data.DraftPage
import com.nobi.scan.data.ScanPreferences
import androidx.compose.runtime.mutableStateListOf
import com.nobi.scan.data.DocumentLibrary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nobi.scan.ui.components.glassPress
import com.nobi.scan.ui.components.PdfNameDialog
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nobi.scan.R
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.DocumentQuadOverlay
import com.nobi.scan.ui.crop.CropEditor
import com.nobi.scan.ui.filter.FilterScreen
import com.nobi.scan.ui.theme.SystemBarContrast
import com.nobi.scan.ui.theme.LocalNobiPalette
import com.nobi.scan.ui.theme.AccentBlue
import com.nobi.scan.ui.theme.DarkBackground
import com.nobi.scan.ui.theme.LiquidPurplePrimary

enum class ScannerScreenState {
    CAMERA,
    CROP,
    FILTER
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun CameraScreen(
    library: DocumentLibrary,
    preferences: ScanPreferences,
    initialPdf: File? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberNobiHaptics()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val autoCapture = remember { AutoCaptureGate() }
    var autoEnabled by remember { mutableStateOf(false) }
    var autoStatus by remember { mutableStateOf(AutoCaptureStatus.FIND_PAGE) }
    var latestFrame by remember { mutableStateOf<AutoCaptureFrame?>(null) }
    var choosingImage by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    // Live OpenCV-detected Quad & processing latency
    var detectedQuad by remember { mutableStateOf(DocumentQuadData.defaultGuide()) }
    var cameraWidth by remember { mutableIntStateOf(480) }
    var cameraHeight by remember { mutableIntStateOf(640) }

    // Screen State Machine
    var screenState by remember { mutableStateOf(ScannerScreenState.CAMERA) }
    SystemBarContrast(
        lightBackground = screenState != ScannerScreenState.CAMERA && !LocalNobiPalette.current.isDark
    )
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var replacementIndex by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    val scannedPages = remember { mutableStateListOf<DraftPage>() }
    val pageCount = scannedPages.size
    var loadingPdf by remember { mutableStateOf(initialPdf != null) }
    var busy by remember { mutableStateOf(initialPdf != null) }
    var naming by remember { mutableStateOf(false) }
    var documentName by remember { mutableStateOf(initialPdf?.let { "${it.nameWithoutExtension} edited" } ?: library.suggestedName()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var searchable by remember { mutableStateOf(preferences.searchable) }
    var exportLanguage by remember { mutableStateOf(preferences.language) }
    var readingPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(initialPdf) {
        if (initialPdf == null) return@LaunchedEffect
        var imported: List<DraftPage> = emptyList()
        try {
            withContext(Dispatchers.IO) { imported = library.editCopy(initialPdf) }
            capturedBitmap = withContext(Dispatchers.IO) { imported.first().loadSource() }
            scannedPages.addAll(imported)
            imported = emptyList()
            screenState = ScannerScreenState.FILTER
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            Toast.makeText(context, "Could not open an editable copy of this PDF", Toast.LENGTH_LONG).show()
            onClose()
        } finally {
            imported.forEach { it.source.delete() }
            loadingPdf = false
            busy = false
        }
    }
    var showDiscard by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    fun keepScanning() {
        if (busy) return
        replacementIndex = null
        capturedBitmap = null
        screenState = ScannerScreenState.CAMERA
    }

    fun openPage(index: Int) {
        if (busy || index !in scannedPages.indices) return
        if (capturedBitmap != null && selectedIndex == index) {
            screenState = ScannerScreenState.FILTER
            return
        }
        busy = true
        val page = scannedPages[index]
        scope.launch {
            try {
                capturedBitmap = withContext(Dispatchers.IO) { page.loadSource() }
                selectedIndex = index
                replacementIndex = null
                screenState = ScannerScreenState.FILTER
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Toast.makeText(context, "Could not open this page", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    fun closeScanner() {
        if (busy) return
        val original = replacementIndex
        if (original != null) openPage(original)
        else if (scannedPages.isEmpty()) onClose() else showDiscard = true
    }

    fun saveDocument(share: Boolean) {
        if (busy || scannedPages.isEmpty()) return
        nameError = library.nameError(documentName)
        if (nameError != null) return
        val name = documentName
        busy = true
        val pages = scannedPages.toList()
        val includeText = searchable
        val language = exportLanguage
        scope.launch {
            try {
                val (file, pagesWithText) = withContext(Dispatchers.IO) {
                    if (includeText) {
                        library.saveSearchableDraft(pages, name, language) { page ->
                            withContext(Dispatchers.Main) { readingPage = page }
                        }
                    } else {
                        library.saveDraft(pages, name) to 0
                    }
                }
                naming = false
                haptics.confirm()
                val message = when {
                    !includeText -> "PDF saved to Documents"
                    pagesWithText < pages.size -> "Searchable text saved on $pagesWithText of ${pages.size} pages"
                    else -> "Searchable PDF saved to Documents"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onClose()
                if (share) {
                    try {
                        context.startActivity(library.shareIntent(file))
                    } catch (exception: ActivityNotFoundException) {
                        Toast.makeText(context, "No app available for sharing", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (includeText) {
                    nameError = exception.message?.takeIf { it.startsWith("No text was recognized") }
                        ?: "Could not make this PDF searchable."
                } else {
                    Toast.makeText(context, "Could not save draft. Check each page’s crop and try again.", Toast.LENGTH_LONG).show()
                }
            } catch (exception: LinkageError) {
                nameError = "Text recognition could not start."
            } finally {
                readingPage = null
                busy = false
            }
        }
    }

    suspend fun addDraftPage(bitmap: Bitmap, replacing: Int?): Int {
        val quad = withContext(Dispatchers.Default) { detectStillDocument(context, bitmap) }
        var source: java.io.File? = null
        try {
            withContext(Dispatchers.IO) { source = library.draftPage(bitmap) }
            val page = DraftPage(checkNotNull(source), quad, filter = preferences.filter)
            val index = if (replacing == null) {
                scannedPages.add(page)
                scannedPages.lastIndex
            } else {
                val previous = scannedPages.set(replacing, page)
                previous.source.delete()
                replacing
            }
            source = null
            return index
        } finally {
            source?.delete()
        }
    }

    fun reviewPreparedPage(index: Int, bitmap: Bitmap) {
        selectedIndex = index
        replacementIndex = null
        capturedBitmap = bitmap
        screenState = ScannerScreenState.FILTER
    }

    fun capturePhoto() {
        val capture = imageCaptureInstance ?: return
        if (busy || choosingImage || screenState != ScannerScreenState.CAMERA ||
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        busy = true
        if (autoEnabled) autoCapture.markCaptured(latestFrame)
        haptics.tap()

        fun captureFailed() {
            busy = false
            autoEnabled = false
            autoCapture.resetSession()
        }

        triggerPhotoCapture(capture, context, onFailure = ::captureFailed) { bitmap ->
            scope.launch {
                try {
                    reviewPreparedPage(addDraftPage(bitmap, replacementIndex), bitmap)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    captureFailed()
                    Toast.makeText(context, "Could not prepare capture", Toast.LENGTH_SHORT).show()
                } finally {
                    busy = false
                }
            }
        }
    }

    fun retakePage() {
        if (busy) return
        replacementIndex = selectedIndex
        capturedBitmap = null
        screenState = ScannerScreenState.CAMERA
    }

    fun movePage(offset: Int) {
        val target = selectedIndex + offset
        if (busy || target !in scannedPages.indices) return
        val page = scannedPages.removeAt(selectedIndex)
        scannedPages.add(target, page)
        selectedIndex = target
    }

    fun importSelectedImages(uris: List<Uri>) {
        choosingImage = false
        if (uris.isEmpty() || busy) return

        val replacing = replacementIndex
        val selected = if (replacing == null) uris else uris.take(1)
        busy = true
        importStatus = "Importing images…"
        scope.launch {
            var firstBitmap: Bitmap? = null
            var firstIndex = 0
            var failures = 0
            try {
                selected.forEachIndexed { index, uri ->
                    importStatus = "Importing ${index + 1} of ${selected.size}…"
                    var bitmap: Bitmap? = null
                    try {
                        withContext(Dispatchers.IO) {
                            bitmap = ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(context.contentResolver, uri)
                            ) { decoder, info, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                val factor = minOf(1f, 3200f / maxOf(info.size.width, info.size.height))
                                decoder.setTargetSize(
                                    (info.size.width * factor).toInt().coerceAtLeast(1),
                                    (info.size.height * factor).toInt().coerceAtLeast(1)
                                )
                            }
                        }
                        val decoded = checkNotNull(bitmap)
                        val preparedIndex = addDraftPage(decoded, replacing)
                        if (firstBitmap == null) {
                            firstIndex = preparedIndex
                            firstBitmap = decoded
                            bitmap = null
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failures++
                    } finally {
                        bitmap?.recycle()
                    }
                }

                firstBitmap?.let {
                    reviewPreparedPage(firstIndex, it)
                    firstBitmap = null
                    haptics.confirm()
                }
                if (failures > 0) {
                    val message = when {
                        selected.size == 1 -> "Could not import image"
                        failures == selected.size -> "Could not import the selected images"
                        else -> "Imported ${selected.size - failures} of ${selected.size} images"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                firstBitmap?.recycle()
                importStatus = null
                busy = false
            }
        }
    }

    val batchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> importSelectedImages(uris) }
    val replacementPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> importSelectedImages(listOfNotNull(uri)) }

    fun importImages() {
        if (busy || choosingImage) return
        autoCapture.resetTracking()
        choosingImage = true
        try {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (replacementIndex == null) batchPicker.launch(request) else replacementPicker.launch(request)
        } catch (_: ActivityNotFoundException) {
            choosingImage = false
            Toast.makeText(context, "No image picker available", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { scannedPages.forEach { it.source.delete() } }
    }
    DisposableEffect(lifecycleOwner, autoCapture) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                autoCapture.resetTracking()
                autoStatus = AutoCaptureStatus.FIND_PAGE
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        if (!busy) {
            haptics.tap()
            when (screenState) {
                ScannerScreenState.CAMERA -> closeScanner()
                ScannerScreenState.CROP -> screenState = ScannerScreenState.FILTER
                ScannerScreenState.FILTER -> keepScanning()
            }
        }
    }
    if (naming) PdfNameDialog(
        title = "Name your PDF",
        name = documentName,
        onNameChange = {
            documentName = it
            nameError = null
        },
        busy = busy,
        error = nameError,
        confirmLabel = "Save PDF",
        onConfirm = { saveDocument(share = false) },
        onShare = { saveDocument(share = true) },
        onDismiss = { naming = false },
        options = {
            SearchablePdfOptions(
                checked = searchable,
                language = exportLanguage,
                busy = busy,
                readingPage = readingPage,
                pageCount = pageCount,
                onChecked = {
                    searchable = it
                    nameError = null
                },
                onLanguage = {
                    exportLanguage = it
                    nameError = null
                }
            )
        }
    )
    if (loadingPdf) {
        Box(Modifier.fillMaxSize().background(LocalNobiPalette.current.background),
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = haptics.onTap { showDiscard = false },
        title = { Text("Discard scan?") },
        text = { Text("The pages in this draft will be removed.") },
        confirmButton = { TextButton(onClick = haptics.onTap(onClose)) { Text("Discard") } },
        dismissButton = { TextButton(onClick = haptics.onTap { showDiscard = false }) { Text("Keep scanning") } }
    )
    deleteIndex?.let { index ->
        AlertDialog(
            onDismissRequest = haptics.onTap { deleteIndex = null },
            title = { Text("Delete page ${index + 1}?") },
            text = { Text("This page will be removed from the draft.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteIndex = null
                    scannedPages.removeAt(index).source.delete()
                    haptics.confirm()
                    keepScanning()
                    if (scannedPages.isNotEmpty()) openPage(index.coerceAtMost(scannedPages.lastIndex))
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = haptics.onTap { deleteIndex = null }) { Text("Cancel") } }
        )
    }

    if (cameraPermissionState.status.isGranted || screenState != ScannerScreenState.CAMERA) {
        val currentBitmap = capturedBitmap
        val currentPage = scannedPages.getOrNull(selectedIndex)

        when (screenState) {
            ScannerScreenState.CROP -> {
                if (currentBitmap != null && currentPage != null) {
                    CropEditor(
                        bitmap = currentBitmap,
                        page = currentPage,
                        onBack = {
                            screenState = ScannerScreenState.FILTER
                        },
                        onApply = { editedPage ->
                            scannedPages[selectedIndex] = editedPage
                            screenState = ScannerScreenState.FILTER
                        }
                    )
                } else {
                    screenState = ScannerScreenState.CAMERA
                }
            }

            ScannerScreenState.FILTER -> {
                if (currentBitmap != null && currentPage != null) {
                    FilterScreen(
                        rawBitmap = currentBitmap,
                        pages = scannedPages.toList(),
                        selectedIndex = selectedIndex,
                        saving = busy,
                        ocrLanguage = exportLanguage,
                        onOcrLanguageChange = { exportLanguage = it },
                        onSelectPage = ::openPage,
                        onMovePage = ::movePage,
                        onFilterChange = { scannedPages[selectedIndex] = currentPage.copy(filter = it) },
                        onRotate = {
                            scannedPages[selectedIndex] = currentPage.rotateRight()
                        },
                        onRetake = ::retakePage,
                        onDelete = { deleteIndex = selectedIndex },
                        onCrop = { if (!busy) screenState = ScannerScreenState.CROP },
                        onKeepScanning = ::keepScanning,
                        onSaveDocument = { naming = true }
                    )
                } else {
                    screenState = ScannerScreenState.CAMERA
                }
            }

            ScannerScreenState.CAMERA -> {
                val trayShape = RoundedCornerShape(24.dp)
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121116))
                        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF211E29), trayShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CameraGlassControl("Back", enabled = !busy, onClick = haptics.onTap(::closeScanner)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text(
                            replacementIndex?.let { "Rescan page ${it + 1}" } ?: "Scan document",
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        CameraFlashButton(camera = boundCamera, enabled = !busy)
                    }

                    Box(
                        Modifier.weight(1f).fillMaxWidth().clip(trayShape).background(Color.Black)
                    ) {
                        CameraPreviewView(
                            onCameraReady = { camera, capture ->
                                boundCamera = camera
                                imageCaptureInstance = capture
                                latestFrame = null
                                autoCapture.resetTracking()
                                autoStatus = AutoCaptureStatus.FIND_PAGE
                                if (capture == null) detectedQuad = DocumentQuadData.defaultGuide()
                                else cameraUnavailable = false
                            },
                            onQuadDetected = { quad, frame ->
                                detectedQuad = quad
                                cameraWidth = frame.cameraWidth
                                cameraHeight = frame.cameraHeight
                                latestFrame = frame
                                if (autoEnabled && !busy && !choosingImage && !showDiscard && !naming &&
                                    imageCaptureInstance != null &&
                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                ) {
                                    autoStatus = autoCapture.evaluate(frame, SystemClock.elapsedRealtime())
                                    if (autoStatus == AutoCaptureStatus.CAPTURE) capturePhoto()
                                } else {
                                    autoCapture.resetTracking()
                                    autoStatus = AutoCaptureStatus.FIND_PAGE
                                }
                            },
                            onCameraError = {
                                cameraUnavailable = true
                                Toast.makeText(context, "Camera unavailable. You can import an image.",
                                    Toast.LENGTH_LONG).show()
                            }
                        )

                        // Dynamic Scanning Quad Overlay (Fed by OpenCV Detector)
                        DocumentQuadOverlay(
                            quadData = detectedQuad,
                            cameraWidth = cameraWidth,
                            cameraHeight = cameraHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    val documentDetected = detectedQuad.confidence >= .65f && imageCaptureInstance != null
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF211E29), trayShape)
                            .border(1.dp, Color.White.copy(alpha = .12f), trayShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (documentDetected && !busy && !autoEnabled) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = Color(0xFFC6ADFF), modifier = Modifier.size(16.dp))
                            }
                            Text(
                                when {
                                    busy -> importStatus ?: "Preparing scan…"
                                    cameraUnavailable -> "Camera unavailable · Import an image"
                                    imageCaptureInstance == null -> "Camera starting…"
                                    autoEnabled -> when (autoStatus) {
                                        AutoCaptureStatus.LOW_LIGHT -> "More light needed"
                                        AutoCaptureStatus.HOLD_STEADY -> "Hold steady…"
                                        AutoCaptureStatus.NEXT_PAGE -> "Move to the next page"
                                        AutoCaptureStatus.CAPTURE -> "Capturing…"
                                        AutoCaptureStatus.FIND_PAGE -> "Fit all four corners"
                                    }
                                    documentDetected -> "Document detected"
                                    else -> "Fit all four corners in the frame"
                                },
                                modifier = Modifier.weight(1f),
                                color = if (documentDetected) Color(0xFFF5F1FB) else Color(0xFFBFB6CD),
                                fontSize = 13.sp
                            )
                            CameraAutoToggle(
                                checked = autoEnabled,
                                enabled = !busy && !choosingImage && imageCaptureInstance != null,
                                onChange = {
                                    autoEnabled = it
                                    autoCapture.resetSession()
                                    autoStatus = AutoCaptureStatus.FIND_PAGE
                                    haptics.toggle(it)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                CameraGlassControl(
                                    label = if (replacementIndex == null) "Import images" else "Import replacement image",
                                    enabled = !busy && !choosingImage,
                                    onClick = haptics.onTap(::importImages)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null, tint = Color.White)
                                }
                                Text("Import", color = Color(0xFFF5F1FB), fontSize = 12.sp)
                            }

                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                CameraGlassControl(
                                    label = "Capture photo",
                                    enabled = !busy && imageCaptureInstance != null,
                                    shutter = true,
                                    onClick = ::capturePhoto
                                ) {}

                            }

                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                CameraGlassControl(
                                    label = "Review draft, $pageCount pages",
                                    enabled = !busy && pageCount > 0,
                                    onClick = haptics.onTap { if (pageCount > 0) openPage(selectedIndex.coerceIn(scannedPages.indices)) }
                                ) {
                                    val stackProgress = remember { Animatable(0f) }
                                    LaunchedEffect(pageCount) {
                                        stackProgress.animateTo(
                                            targetValue = (pageCount - 1).coerceIn(0, 2).toFloat(),
                                            animationSpec = spring(dampingRatio = .65f, stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                    if (pageCount > 1) {
                                        Icon(Icons.Default.Description, null,
                                            modifier = Modifier.graphicsLayer {
                                                translationX = stackProgress.value * 2.dp.toPx()
                                                rotationZ = stackProgress.value * 6f
                                            }, tint = Color.White.copy(alpha = .48f))
                                    }
                                    Icon(Icons.Default.Description, null, tint = Color.White)

                                    if (pageCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .align(Alignment.TopEnd)
                                                .clip(CircleShape)
                                                .background(LiquidPurplePrimary)
                                                .border(1.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pageCount.toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text("Pages", color = Color(0xFFF5F1FB), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Permission Request State
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = importStatus ?: stringResource(R.string.camera_permission_required),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = haptics.onTap { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text(text = stringResource(R.string.grant_permission))
                }
                TextButton(onClick = haptics.onTap(::importImages), enabled = !busy && !choosingImage) {
                    Text(if (replacementIndex == null) "Import images" else "Import replacement image")
                }
                if (pageCount > 0) TextButton(onClick = haptics.onTap { if (pageCount > 0) openPage(selectedIndex.coerceIn(scannedPages.indices)) }) { Text("Draft pages") }
                TextButton(onClick = haptics.onTap(::closeScanner), enabled = !busy) { Text("Back") }
            }
        }
    }
}

@Composable
private fun CameraAutoToggle(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(48.dp).clip(shape)
            .background(if (checked) Color(0xFFC6ADFF) else Color(0xFF353140))
            .border(1.dp, if (checked) Color(0xFFC6ADFF) else Color(0xFF777080), shape)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (checked) "Auto on" else "Auto off",
            color = if (checked) Color(0xFF221637) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CameraFlashButton(camera: Camera?, enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberNobiHaptics()
    val torchState = camera?.cameraInfo?.torchState
    var isOn by remember(camera) { mutableStateOf(torchState?.value == TorchState.ON) }
    var changing by remember(camera) { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    DisposableEffect(torchState, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<Int> { isOn = it == TorchState.ON }
        torchState?.observe(lifecycleOwner, observer)
        onDispose { torchState?.removeObserver(observer) }
    }

    val label = when {
        camera == null -> "Flash"
        !hasFlash -> "No flash"
        isOn -> "Flash on"
        else -> "Flash off"
    }
    val foreground = if (isOn) Color(0xFF281D09) else Color.White
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier.clip(shape)
            .background(if (isOn) Color(0xFFFDE397) else Color(0xFF353140))
            .border(1.dp, if (isOn) Color(0xFFFDE397) else Color(0xFF777080), shape)
            .clickable(
                enabled = enabled && hasFlash && !changing,
                role = Role.Button,
                onClickLabel = if (isOn) "Turn flash off" else "Turn flash on"
            ) {
                val control = camera?.cameraControl ?: return@clickable
                val turnOn = !isOn
                haptics.toggle(turnOn)
                changing = true
                val request = control.enableTorch(turnOn)
                request.addListener({
                    changing = false
                    try {
                        request.get()
                    } catch (exception: java.util.concurrent.ExecutionException) {
                        if (exception.cause !is androidx.camera.core.CameraControl.OperationCanceledException) {
                            Toast.makeText(context, "Could not change flash", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            .height(50.dp).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null,
            modifier = Modifier.size(18.dp), tint = foreground)
        Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraGlassControl(
    label: String,
    enabled: Boolean,
    shutter: Boolean = false,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(if (shutter) 76.dp else 50.dp)
            .shadow(if (shutter) 8.dp else 3.dp, CircleShape)
            .clip(CircleShape)
            .glassPress(interactions)
            .background(Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x45362B3B))))
            .border(1.dp, Color.White.copy(alpha = .64f), CircleShape)
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (shutter) {
            Box(
                Modifier.size(63.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, Color(0xFFE7DEEB))))
                    .border(2.dp, Color.White, CircleShape)
            )
        }
        content()
    }
}

private fun triggerPhotoCapture(
    capture: ImageCapture,
    context: Context,
    onFailure: () -> Unit,
    onSuccess: (Bitmap) -> Unit
) {
    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val fullBitmap = image.toBitmap()
                    val crop = image.cropRect
                    val rawBitmap = Bitmap.createBitmap(fullBitmap, crop.left, crop.top, crop.width(), crop.height())
                    if (rawBitmap !== fullBitmap) fullBitmap.recycle()
                    val rotatedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    } else {
                        rawBitmap
                    }
                    if (rotatedBitmap !== rawBitmap) rawBitmap.recycle()
                    onSuccess(rotatedBitmap)
                } catch (e: Exception) {
                    onFailure()
                    Toast.makeText(context, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure()
                Toast.makeText(context, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
