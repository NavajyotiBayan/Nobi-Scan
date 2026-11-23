package com.nobi.scan.cv

import android.graphics.Bitmap
import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2

data class PreviewDetection(val quad: DocumentQuadData, val hasFourCorners: Boolean)

class DocumentDetector {

    companion object {
        private val available by lazy {
            try {
                OpenCVLoader.initLocal()
            } catch (exception: LinkageError) {
                android.util.Log.w("NobiScan", "OpenCV unavailable", exception)
                false
            }
        }
    }

    /**
     * Real-time detector for camera preview YUV Y-plane luminance frames.
     */
    fun detectFromYBuffer(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): PreviewDetection {
        if (!available || width < 32 || height < 32 || yBytes.size < width * height) {
            return PreviewDetection(DocumentQuadData.defaultGuide(), false)
        }

        // Subsample for fast real-time 30+ FPS processing (max dimension 640)
        val maxDim = 640
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(width, height))
        val targetW = (width * scale).toInt().coerceAtLeast(32)
        val targetH = (height * scale).toInt().coerceAtLeast(32)

        val srcMat = Mat(height, width, CvType.CV_8UC1)
        val downsampled = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()

        try {
            srcMat.put(0, 0, yBytes)
            Imgproc.resize(srcMat, downsampled, Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(downsampled, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            Imgproc.dilate(edges, dilated, kernel)

            Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = (targetW * targetH).toDouble()
            var bestQuad: DocumentQuadData? = null
            var bestScore = 0.0

            // Fallback candidate if no strict 4-point polygon passes
            var fallbackLargestContour: MatOfPoint? = null
            var fallbackMaxArea = 0.0

            for (contour in contours) {
                val area = abs(Imgproc.contourArea(contour))
                val areaRatio = area / frameArea

                // Candidate document occupies between 10% and 95% of frame
                if (areaRatio in 0.10..0.95) {
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val perimeter = Imgproc.arcLength(contour2f, true)
                    val approx = MatOfPoint2f()

                    Imgproc.approxPolyDP(contour2f, approx, 0.025 * perimeter, true)
                    val points = approx.toArray()

                    if (points.size == 4) {
                        val approxOfPoint = MatOfPoint(*points)
                        if (Imgproc.isContourConvex(approxOfPoint)) {
                            val anglesOk = checkOrthogonality(points)
                            val score = areaRatio * if (anglesOk) 1.6 else 0.85

                            if (score > bestScore) {
                                bestScore = score
                                val ordered = orderAndNormalize(points, targetW, targetH)
                                val rotated = rotateNormalizedQuad(ordered, rotationDegrees)
                                bestQuad = rotated.copy(confidence = (score * 1.35).coerceIn(0.68, 0.98).toFloat())
                            }
                        }
                        approxOfPoint.release()
                    }

                    if (area > fallbackMaxArea) {
                        fallbackMaxArea = area
                        fallbackLargestContour = contour
                    }

                    approx.release()
                    contour2f.release()
                }
            }

            val hasFourCorners = bestQuad != null

            // Fallback: If no strict 4-vertex polygon was found, use minAreaRect on largest contour (FairScan / DocScannerPro approach)
            if (bestQuad == null && fallbackLargestContour != null) {
                val contour2f = MatOfPoint2f(*fallbackLargestContour.toArray())
                val minRect = Imgproc.minAreaRect(contour2f)
                val boxPts = Array(4) { Point() }
                minRect.points(boxPts)

                val ordered = orderAndNormalize(boxPts, targetW, targetH)
                val rotated = rotateNormalizedQuad(ordered, rotationDegrees)
                val fbAreaRatio = fallbackMaxArea / frameArea
                bestQuad = rotated.copy(confidence = (fbAreaRatio * 1.1).coerceIn(0.55, 0.85).toFloat())
                contour2f.release()
            }

            val finalResult = bestQuad ?: DocumentQuadData.defaultGuide()
            return PreviewDetection(finalResult, hasFourCorners)
        } catch (e: Exception) {
            e.printStackTrace()
            return PreviewDetection(DocumentQuadData.defaultGuide(), false)
        } finally {
            srcMat.release()
            downsampled.release()
            blurred.release()
            edges.release()
            dilated.release()
            hierarchy.release()
            kernel.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * High-accuracy detector for captured still photo Bitmaps.
     */
    fun detectFromBitmap(bitmap: Bitmap): DocumentQuadData {
        val w = bitmap.width
        val h = bitmap.height
        if (!available || w < 32 || h < 32) return DocumentQuadData.fullPage()

        // Downscale to max dimension 800 for instant ~15ms processing
        val maxDim = 800
        val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h))
        val targetW = (w * scale).toInt().coerceAtLeast(32)
        val targetH = (h * scale).toInt().coerceAtLeast(32)

        val small = if (targetW == w && targetH == h) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }

        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val grayBytes = ByteArray(targetW * targetH)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            grayBytes[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }

        val srcMat = Mat(targetH, targetW, CvType.CV_8UC1)
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()

        try {
            srcMat.put(0, 0, grayBytes)
            Imgproc.GaussianBlur(srcMat, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            Imgproc.dilate(edges, dilated, kernel)

            Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = (targetW * targetH).toDouble()
            var bestQuad: DocumentQuadData? = null
            var bestScore = 0.0

            var fallbackLargestContour: MatOfPoint? = null
            var fallbackMaxArea = 0.0

            for (contour in contours) {
                val area = abs(Imgproc.contourArea(contour))
                val areaRatio = area / frameArea

                if (areaRatio in 0.10..0.96) {
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val perimeter = Imgproc.arcLength(contour2f, true)
                    val approx = MatOfPoint2f()

                    Imgproc.approxPolyDP(contour2f, approx, 0.025 * perimeter, true)
                    val points = approx.toArray()

                    if (points.size == 4) {
                        val approxOfPoint = MatOfPoint(*points)
                        if (Imgproc.isContourConvex(approxOfPoint)) {
                            val anglesOk = checkOrthogonality(points)
                            val score = areaRatio * if (anglesOk) 1.6 else 0.85

                            if (score > bestScore) {
                                bestScore = score
                                val ordered = orderAndNormalize(points, targetW, targetH)
                                bestQuad = ordered.copy(confidence = (score * 1.35).coerceIn(0.70, 0.99).toFloat())
                            }
                        }
                        approxOfPoint.release()
                    }

                    if (area > fallbackMaxArea) {
                        fallbackMaxArea = area
                        fallbackLargestContour = contour
                    }

                    approx.release()
                    contour2f.release()
                }
            }

            // Fallback to minAreaRect for largest contour
            if (bestQuad == null && fallbackLargestContour != null) {
                val contour2f = MatOfPoint2f(*fallbackLargestContour.toArray())
                val minRect = Imgproc.minAreaRect(contour2f)
                val boxPts = Array(4) { Point() }
                minRect.points(boxPts)

                val ordered = orderAndNormalize(boxPts, targetW, targetH)
                val fbAreaRatio = fallbackMaxArea / frameArea
                bestQuad = ordered.copy(confidence = (fbAreaRatio * 1.1).coerceIn(0.65, 0.90).toFloat())
                contour2f.release()
            }

            return bestQuad ?: DocumentQuadData.fullPage()
        } catch (e: Exception) {
            e.printStackTrace()
            return DocumentQuadData.fullPage()
        } finally {
            srcMat.release()
            blurred.release()
            edges.release()
            dilated.release()
            hierarchy.release()
            kernel.release()
            contours.forEach { it.release() }
            if (small !== bitmap) {
                small.recycle()
            }
        }
    }

    private fun checkOrthogonality(pts: Array<Point>): Boolean {
        for (i in 0 until 4) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]

            val angle1 = atan2(p0.y - p1.y, p0.x - p1.x)
            val angle2 = atan2(p2.y - p1.y, p2.x - p1.x)
            var diff = abs(Math.toDegrees(angle1 - angle2))
            if (diff > 180.0) diff = 360.0 - diff

            if (diff < 55.0 || diff > 125.0) {
                return false
            }
        }
        return true
    }

    private fun orderAndNormalize(pts: Array<Point>, w: Int, h: Int): DocumentQuadData {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()

        val remaining = sortedBySum.drop(1).dropLast(1)
        val tr = remaining.maxByOrNull { it.x - it.y } ?: remaining[0]
        val bl = remaining.minByOrNull { it.x - it.y } ?: remaining[1]

        fun norm(p: Point): QuadPoint = QuadPoint(
            x = (p.x / w).toFloat().coerceIn(0f, 1f),
            y = (p.y / h).toFloat().coerceIn(0f, 1f)
        )

        return DocumentQuadData(
            topLeft = norm(tl),
            topRight = norm(tr),
            bottomRight = norm(br),
            bottomLeft = norm(bl),
            confidence = 0.85f
        )
    }

    private fun rotateNormalizedQuad(quad: DocumentQuadData, rotationDegrees: Int): DocumentQuadData {
        return when (rotationDegrees) {
            90 -> DocumentQuadData(
                topLeft = QuadPoint(1f - quad.bottomLeft.y, quad.bottomLeft.x),
                topRight = QuadPoint(1f - quad.topLeft.y, quad.topLeft.x),
                bottomRight = QuadPoint(1f - quad.topRight.y, quad.topRight.x),
                bottomLeft = QuadPoint(1f - quad.bottomRight.y, quad.bottomRight.x),
                confidence = quad.confidence
            )
            270 -> DocumentQuadData(
                topLeft = QuadPoint(quad.topRight.y, 1f - quad.topRight.x),
                topRight = QuadPoint(quad.bottomRight.y, 1f - quad.bottomRight.x),
                bottomRight = QuadPoint(quad.bottomLeft.y, 1f - quad.bottomLeft.x),
                bottomLeft = QuadPoint(quad.topLeft.y, 1f - quad.topLeft.x),
                confidence = quad.confidence
            )
            180 -> DocumentQuadData(
                topLeft = QuadPoint(1f - quad.bottomRight.x, 1f - quad.bottomRight.y),
                topRight = QuadPoint(1f - quad.bottomLeft.x, 1f - quad.bottomLeft.y),
                bottomRight = QuadPoint(1f - quad.topLeft.x, 1f - quad.topLeft.y),
                bottomLeft = QuadPoint(1f - quad.topRight.x, 1f - quad.topRight.y),
                confidence = quad.confidence
            )
            else -> quad
        }
    }

}
