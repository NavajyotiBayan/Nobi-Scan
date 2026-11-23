package com.nobi.scan.ui.camera

import com.nobi.scan.ui.camera.components.DocumentQuadData
import com.nobi.scan.ui.camera.components.QuadPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCaptureGateTest {
    private val gate = AutoCaptureGate()

    @Test fun waitsForStableFramesBeforeCapturing() {
        for (time in 0L..800L step 100) {
            assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(time))
        }
        assertEquals(AutoCaptureStatus.CAPTURE, evaluate(900))
    }

    @Test fun waitsWhenDarkClippedOrFallback() {
        assertEquals(AutoCaptureStatus.LOW_LIGHT, evaluate(0, brightness = 38))
        assertEquals(AutoCaptureStatus.FIND_PAGE, evaluate(100, offset = -.18f))
        assertEquals(AutoCaptureStatus.FIND_PAGE, evaluate(200, corners = false))
    }

    @Test fun checksThatCornersAreInsideTheAspectFillViewport() {
        val frame = frame(0).copy(cameraWidth = 640, cameraHeight = 480, viewWidth = 320, viewHeight = 640)
        assertEquals(AutoCaptureStatus.FIND_PAGE, gate.evaluate(frame, 0))
    }

    @Test fun motionAndFrameGapsRestartTheHold() {
        for (time in 0L..600L step 100) evaluate(time)
        assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(700, brightness = 145))
        assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(800, brightness = 145, offset = .06f))
        assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(1700, brightness = 145, offset = .06f))
        assertEquals(AutoCaptureStatus.FIND_PAGE, gate.evaluate(frame(1800), 2300))
    }

    @Test fun pausesForSamePageUntilItIsRemoved() {
        gate.markCaptured(frame(0))
        for (time in 100L..700L step 100) {
            assertEquals(AutoCaptureStatus.NEXT_PAGE, evaluate(time))
        }
        for (time in 800L..1300L step 100) {
            assertEquals(AutoCaptureStatus.FIND_PAGE, evaluate(time, corners = false))
        }
        for (time in 1400L..2200L step 100) {
            assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(time))
        }
        assertEquals(AutoCaptureStatus.CAPTURE, evaluate(2300))
    }

    @Test fun localizedOcclusionRestartsTheHold() {
        for (time in 0L..800L step 100) evaluate(time)
        val occluded = frame(900).copy(luminance = IntArray(144) { if (it in 30..66) 65 else 120 })
        assertEquals(AutoCaptureStatus.HOLD_STEADY, gate.evaluate(occluded, 900))
        assertEquals(AutoCaptureStatus.HOLD_STEADY, evaluate(1000))
    }

    @Test fun capturesAnAngledPageThatRemainsInsideTheFrame() {
        val angled = DocumentQuadData(
            QuadPoint(.23f, .15f), QuadPoint(.83f, .25f),
            QuadPoint(.70f, .84f), QuadPoint(.15f, .74f), .81f
        )
        for (time in 0L..800L step 100) {
            assertEquals(AutoCaptureStatus.HOLD_STEADY, gate.evaluate(frame(time).copy(quad = angled), time))
        }
        assertEquals(AutoCaptureStatus.CAPTURE, gate.evaluate(frame(900).copy(quad = angled), 900))
    }

    private fun evaluate(time: Long, brightness: Int = 120, offset: Float = 0f, corners: Boolean = true) =
        gate.evaluate(frame(time, brightness, offset, corners), time)

    private fun frame(time: Long, brightness: Int = 120, offset: Float = 0f, corners: Boolean = true) =
        AutoCaptureFrame(
            DocumentQuadData(
                QuadPoint(.15f + offset, .20f), QuadPoint(.85f + offset, .20f),
                QuadPoint(.85f + offset, .80f), QuadPoint(.15f + offset, .80f), .9f
            ),
            corners, IntArray(144) { brightness }, time, 480, 640, 480, 640
        )
}
