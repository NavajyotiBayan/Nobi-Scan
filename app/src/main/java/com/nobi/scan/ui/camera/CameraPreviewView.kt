package com.nobi.scan.ui.camera

import android.util.Size
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.nobi.scan.cv.DocumentDetector
import com.nobi.scan.cv.QuadStabilizer
import com.nobi.scan.ui.camera.components.DocumentQuadData
import java.util.concurrent.Executors

@Composable
internal fun CameraPreviewView(
    onCameraReady: (Camera?, ImageCapture?) -> Unit,
    onQuadDetected: (DocumentQuadData, AutoCaptureFrame) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val ready by rememberUpdatedState(onCameraReady)
    val detected by rememberUpdatedState(onQuadDetected)
    val showError by rememberUpdatedState(onCameraError)
    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())

    DisposableEffect(previewView, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val detector = DocumentDetector()
        val stabilizer = QuadStabilizer()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(2560, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            ).build())
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            ).build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor) { image ->
            try {
                val timestamp = SystemClock.elapsedRealtime()
                val crop = image.cropRect
                val luma = image.planes[0]
                val buffer = luma.buffer
                val base = buffer.position()
                val pixels = ByteArray(crop.width() * crop.height())
                for (row in 0 until crop.height()) {
                    val rowStart = base + (crop.top + row) * luma.rowStride + crop.left * luma.pixelStride
                    if (luma.pixelStride == 1) {
                        buffer.position(rowStart)
                        buffer.get(pixels, row * crop.width(), crop.width())
                    } else {
                        for (column in 0 until crop.width()) {
                            pixels[row * crop.width() + column] = buffer.get(rowStart + column * luma.pixelStride)
                        }
                    }
                }
                val rotation = image.imageInfo.rotationDegrees
                val result = detector.detectFromYBuffer(pixels, crop.width(), crop.height(), rotation)
                val quad = stabilizer.update(result.quad)
                val swapped = rotation == 90 || rotation == 270
                val width = if (swapped) crop.height() else crop.width()
                val height = if (swapped) crop.width() else crop.height()
                val samples = sampleLuminance(pixels, crop.width(), crop.height())
                mainExecutor.execute {
                    if (!disposed) {
                        detected(quad, AutoCaptureFrame(
                            quad = result.quad,
                            hasFourCorners = result.hasFourCorners,
                            luminance = samples,
                            timestampMs = timestamp,
                            cameraWidth = width,
                            cameraHeight = height,
                            viewWidth = previewView.width,
                            viewHeight = previewView.height
                        ))
                    }
                }
            } catch (exception: RuntimeException) {
                android.util.Log.w("NobiScan", "Could not analyze camera frame", exception)
            } finally {
                image.close()
            }
        }

        previewView.doOnLayout {
            providerFuture.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = providerFuture.get()
                        provider = cameraProvider
                        val viewport = checkNotNull(previewView.viewPort)
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        val group = UseCaseGroup.Builder().setViewPort(viewport)
                            .addUseCase(preview).addUseCase(capture).addUseCase(analysis).build()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA, group)
                        ready(camera, capture)
                    } catch (exception: Exception) {
                        android.util.Log.w("NobiScan", "Could not start camera", exception)
                        showError()
                    }
                }
            }, mainExecutor)
        }

        onDispose {
            disposed = true
            ready(null, null)
            analysis.clearAnalyzer()
            provider?.unbind(preview, capture, analysis)
            executor.shutdown()
        }
    }
}


// Sample the middle of the frame to detect exposure changes and movement in the scene.
private fun sampleLuminance(pixels: ByteArray, width: Int, height: Int): IntArray =
    IntArray(144) { index ->
        val x = ((index % 12 + 2) * width / 16).coerceIn(0, width - 1)
        val y = ((index / 12 + 2) * height / 16).coerceIn(0, height - 1)
        pixels[y * width + x].toInt() and 255
    }
