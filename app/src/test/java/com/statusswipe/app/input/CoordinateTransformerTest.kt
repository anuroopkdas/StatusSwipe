package com.statusswipe.app.input

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformerTest {

    @Test
    fun testRotation0_noTransform() {
        val (x, y) = CoordinateTransformer.transformRotation(0.2f, 0.3f, Surface.ROTATION_0)
        assertEquals(0.2f, x, 0.001f)
        assertEquals(0.3f, y, 0.001f)
    }

    @Test
    fun testRotation90_landscape() {
        val (x, y) = CoordinateTransformer.transformRotation(0.2f, 0.3f, Surface.ROTATION_90)
        assertEquals(0.3f, x, 0.001f)
        assertEquals(0.8f, y, 0.001f) // 1.0 - 0.2
    }

    @Test
    fun testRotation180_inverted() {
        val (x, y) = CoordinateTransformer.transformRotation(0.2f, 0.3f, Surface.ROTATION_180)
        assertEquals(0.8f, x, 0.001f) // 1.0 - 0.2
        assertEquals(0.7f, y, 0.001f) // 1.0 - 0.3
    }

    @Test
    fun testRotation270_landscapeReverse() {
        val (x, y) = CoordinateTransformer.transformRotation(0.2f, 0.3f, Surface.ROTATION_270)
        assertEquals(0.7f, x, 0.001f) // 1.0 - 0.3
        assertEquals(0.2f, y, 0.001f)
    }
}
