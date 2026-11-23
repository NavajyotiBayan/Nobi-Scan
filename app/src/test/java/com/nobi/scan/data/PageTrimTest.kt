package com.nobi.scan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PageTrimTest {
    @Test fun rotateMovesCropToMatchingPartOfThePage() {
        val region = TrimRegion(.15f, .10f, .60f, .75f).rotateRight()
        assertEquals(.25f, region.left, .0001f)
        assertEquals(.15f, region.top, .0001f)
        assertEquals(.90f, region.right, .0001f)
        assertEquals(.60f, region.bottom, .0001f)
    }

    @Test fun a4CropUsesImagePixelDimensions() {
        val ratio = checkNotNull(TrimRatio.A4.normalized(1000, 2000))
        val fitted = TrimRegion().fitRatio(ratio)
        val dragged = fitted.dragCorner(2, .55f, .7f, ratio)
        assertEquals(210f / 297, dragged.width * 1000 / (dragged.height * 2000), .0001f)
        assertEquals(fitted.left, dragged.left, .0001f)
        assertEquals(fitted.top, dragged.top, .0001f)
    }

    @Test fun freeHandleStaysInsideTheImageWithoutCrossingTheAnchor() {
        val region = TrimRegion(.15f, .20f, .60f, .75f)
        val grown = region.dragCorner(0, -.1f, -.2f, null)
        assertEquals(0f, grown.left, .0001f)
        assertEquals(0f, grown.top, .0001f)
        val shrunk = region.dragCorner(0, .8f, .9f, null)
        assertTrue(shrunk.width >= .0399f)
        assertTrue(shrunk.height >= .0399f)
    }

    @Test fun straighteningKeepsTheVisibleCornersInsideTheImage() {
        val width = 1900f
        val height = 1100f
        for (angle in listOf(-15f, -5f, 0f, 6f, 15f)) {
            val scale = straightenScale(width, height, angle)
            val radians = -angle * PI / 180
            for (x in listOf(-width / 2, width / 2)) {
                for (y in listOf(-height / 2, height / 2)) {
                    val sourceX = (x * cos(radians) - y * sin(radians)) / scale
                    val sourceY = (x * sin(radians) + y * cos(radians)) / scale
                    assertTrue(sourceX in -width / 2 - .001..width / 2 + .001)
                    assertTrue(sourceY in -height / 2 - .001..height / 2 + .001)
                }
            }
        }
    }
}
