package com.nobi.scan.cv

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "NobiDetection"
private const val MODEL = "fairscan-segmentation-model.tflite"

internal fun detectStillDocument(context: Context, bitmap: Bitmap): DocumentQuadData {
    val start = SystemClock.elapsedRealtime()
    val openCv = DocumentDetector().detectFromBitmap(bitmap)
    val openCvMs = SystemClock.elapsedRealtime() - start
    val fallback = if (openCv.confidence <= .72f) {
        try {
            segmentDocument(context, bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "FairScan could not suggest corners", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "FairScan runtime unavailable", e)
            null
        }
    } else null
    val route = when {
        fallback != null -> "fairscan"
        openCv.confidence > 0f -> "opencv"
        else -> "manual"
    }
    Log.i(TAG, "route=$route opencvConfidence=${openCv.confidence} " +
        "opencvMs=$openCvMs detectionMs=${SystemClock.elapsedRealtime() - start}")
    return fallback ?: openCv
}

private fun segmentDocument(context: Context, source: Bitmap): DocumentQuadData? {
    val model = context.assets.open(MODEL).use { it.readBytes() }
    val modelBuffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
    modelBuffer.put(model).rewind()
    Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2)).use { interpreter ->
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val shape = input.shape()
        check(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32)
        check(shape.size == 4 && shape[0] == 1 && shape[3] == 3)
        val height = shape[1]
        val width = shape[2]
        check(output.shape().contentEquals(intArrayOf(1, height, width, 1)))

        val rgb = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        try {
            rgb.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (rgb !== source) rgb.recycle()
        }
        val inputBuffer = ByteBuffer.allocateDirect(pixels.size * 12).order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 255) / 127.5f - 1f)
            inputBuffer.putFloat(((pixel shr 8) and 255) / 127.5f - 1f)
            inputBuffer.putFloat((pixel and 255) / 127.5f - 1f)
        }
        inputBuffer.rewind()
        val outputBuffer = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val mask = ByteArray(pixels.size) { if (outputBuffer.float > .5f) 255.toByte() else 0 }
        return fitMask(mask, width, height)
    }
}

private fun fitMask(bytes: ByteArray, width: Int, height: Int): DocumentQuadData? {
    val mask = Mat(height, width, CvType.CV_8UC1)
    val hierarchy = Mat()
    val contours = ArrayList<MatOfPoint>()
    val outline = MatOfPoint2f()
    val approximation = MatOfPoint2f()
    val polygon = MatOfPoint()
    try {
        mask.put(0, 0, bytes)
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val contour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
        val area = Imgproc.contourArea(contour)
        if (area / (width * height) !in 0.10..0.94) return null
        outline.fromArray(*contour.toArray())
        val perimeter = Imgproc.arcLength(outline, true)
        for (tolerance in listOf(.015, .025, .035)) {
            Imgproc.approxPolyDP(outline, approximation, perimeter * tolerance, true)
            val corners = approximation.toArray()
            if (corners.size != 4) continue
            polygon.fromArray(*corners)
            if (!Imgproc.isContourConvex(polygon) || Imgproc.contourArea(polygon) / area !in 0.80..1.15) continue

            // Convex contour traversal preserves winding. Start at the upper-left corner.
            val first = corners.indices.minBy { corners[it].x / width + corners[it].y / height }
            val step = if (Imgproc.contourArea(polygon, true) > 0) 1 else -1
            fun point(offset: Int): QuadPoint {
                val corner = corners[(first + step * offset + 4) % 4]
                return QuadPoint((corner.x / width).toFloat(), (corner.y / height).toFloat())
            }
            return DocumentQuadData(point(0), point(1), point(2), point(3), confidence = .75f)
        }
        return null
    } finally {
        mask.release()
        hierarchy.release()
        contours.forEach { it.release() }
        outline.release()
        approximation.release()
        polygon.release()
    }
}
