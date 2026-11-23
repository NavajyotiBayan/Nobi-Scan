package com.nobi.scan.ui.camera

import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint
import kotlin.math.abs
import kotlin.math.hypot

internal data class AutoCaptureFrame(
    val quad: DocumentQuadData,
    val hasFourCorners: Boolean,
    val luminance: IntArray,
    val timestampMs: Long,
    val cameraWidth: Int,
    val cameraHeight: Int,
    val viewWidth: Int,
    val viewHeight: Int
)

internal enum class AutoCaptureStatus {
    FIND_PAGE, LOW_LIGHT, HOLD_STEADY, NEXT_PAGE, CAPTURE
}

internal class AutoCaptureGate {
    private var anchor: AutoCaptureFrame? = null
    private var previous: AutoCaptureFrame? = null
    private var consecutiveFrames = 0
    private var lastCaptured: AutoCaptureFrame? = null
    private var missingSince: Long? = null

    fun resetTracking() {
        anchor = null
        previous = null
        consecutiveFrames = 0
        missingSince = null
    }

    fun resetSession() {
        resetTracking()
        lastCaptured = null
    }

    fun markCaptured(frame: AutoCaptureFrame?) {
        lastCaptured = frame?.takeIf(::hasFullPage)
        resetTracking()
    }

    fun evaluate(frame: AutoCaptureFrame, nowMs: Long): AutoCaptureStatus {
        if (nowMs - frame.timestampMs !in 0..MAX_FRAME_GAP_MS) {
            resetTracking()
            return AutoCaptureStatus.FIND_PAGE
        }

        val previousFrame = previous
        if (previousFrame != null && frame.timestampMs - previousFrame.timestampMs !in 1..MAX_FRAME_GAP_MS) {
            resetTracking()
        }
        val prior = previous
        previous = frame

        if (frame.luminance.average() < MIN_LUMINANCE) {
            resetHold()
            missingSince = null
            return AutoCaptureStatus.LOW_LIGHT
        }

        if (!hasFullPage(frame)) {
            resetHold()
            val absentFrom = missingSince ?: frame.timestampMs.also { missingSince = it }
            if (frame.timestampMs - absentFrom >= PAGE_REMOVED_MS) lastCaptured = null
            return AutoCaptureStatus.FIND_PAGE
        }
        missingSince = null

        val captured = lastCaptured
        if (captured != null && cornerDistance(frame.quad, captured.quad) < .10f &&
            sceneDistance(frame, captured) < 15f
        ) {
            resetHold()
            return AutoCaptureStatus.NEXT_PAGE
        }

        val first = anchor
        if (first == null || prior == null ||
            cornerDistance(frame.quad, first.quad) > .04f ||
            cornerDistance(frame.quad, prior.quad) > .026f ||
            sceneDistance(frame, prior) > 9f || sceneDistance(frame, first) > 13f
        ) {
            anchor = frame
            consecutiveFrames = 1
            return AutoCaptureStatus.HOLD_STEADY
        }

        consecutiveFrames++
        return if (frame.timestampMs - first.timestampMs >= HOLD_MS && consecutiveFrames >= MIN_FRAMES) {
            AutoCaptureStatus.CAPTURE
        } else AutoCaptureStatus.HOLD_STEADY
    }

    private fun resetHold() {
        anchor = null
        consecutiveFrames = 0
    }

    private fun hasFullPage(frame: AutoCaptureFrame): Boolean {
        if (!frame.hasFourCorners || frame.quad.confidence < .67f ||
            minOf(frame.cameraWidth, frame.cameraHeight, frame.viewWidth, frame.viewHeight) <= 0
        ) return false

        val scale = maxOf(
            frame.viewWidth.toFloat() / frame.cameraWidth,
            frame.viewHeight.toFloat() / frame.cameraHeight
        )
        val renderedWidth = frame.cameraWidth * scale
        val renderedHeight = frame.cameraHeight * scale
        val xInset = .5f - frame.viewWidth * .48f / renderedWidth
        val yInset = .5f - frame.viewHeight * .48f / renderedHeight
        val corners = frame.quad.corners()

        if (!corners.all { it.x in xInset..(1f - xInset) && it.y in yInset..(1f - yInset) }) return false

        val area = abs(corners.indices.sumOf { index ->
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }) / 2
        return area in .18..0.90
    }

    private fun cornerDistance(a: DocumentQuadData, b: DocumentQuadData): Float =
        a.corners().zip(b.corners()).maxOf { (x, y) -> hypot(x.x - y.x, x.y - y.y) }

    private fun sceneDistance(a: AutoCaptureFrame, b: AutoCaptureFrame): Float =
        a.luminance.indices.sumOf { abs(a.luminance[it] - b.luminance[it]) }.toFloat() / a.luminance.size

    private fun DocumentQuadData.corners(): List<QuadPoint> =
        listOf(topLeft, topRight, bottomRight, bottomLeft)

    private companion object {
        const val HOLD_MS = 900L
        const val MIN_FRAMES = 7
        const val MAX_FRAME_GAP_MS = 350L
        const val PAGE_REMOVED_MS = 500L
        const val MIN_LUMINANCE = 56.0
    }
}
