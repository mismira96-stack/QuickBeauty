package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class PreviewViewportTest {
    @Test fun zoomKeepsTappedImagePointAndResetFitsAgain() {
        val v = PreviewViewport()
        v.configure(500f, 800f, 1000f, 1600f)
        val imageX = (200f - v.left) / v.scale
        val imageY = (300f - v.top) / v.scale
        v.zoomAt(3f, 200f, 300f)
        assertEquals(200f, v.left + imageX * v.scale, 0.001f)
        assertEquals(300f, v.top + imageY * v.scale, 0.001f)
        v.reset()
        assertEquals(0.5f, v.scale, 0.001f)
        assertEquals(0f, v.left, 0.001f)
    }
    @Test fun panClampsAtImageEdgesAndZoomHasBounds() {
        val v = PreviewViewport()
        v.configure(500f, 800f, 1000f, 1600f)
        v.zoomAt(100f, 250f, 400f)
        assertEquals(6f, v.zoom, 0f)
        v.pan(10000f, -10000f)
        assertEquals(0f, v.left, 0.001f)
        assertEquals(800f, v.top + 1600f * v.scale, 0.001f)
        v.zoomAt(0.001f, 50f, 60f)
        assertEquals(1f, v.zoom, 0f)
        assertEquals(0f, v.left, 0f)
        assertEquals(0f, v.top, 0f)
    }
    @Test fun shortImageStaysCenteredAndResizeClampsPan() {
        val v = PreviewViewport()
        v.configure(500f, 800f, 1000f, 500f)
        v.pan(200f, 200f)
        assertEquals(275f, v.top, 0f)
        v.zoomAt(2f, 250f, 400f)
        assertEquals(150f, v.top, 0f)
        v.configure(1000f, 400f, 1000f, 500f)
        assertTrue(v.left <= 0f)
        assertTrue(v.left + 1000f * v.scale >= 1000f)
    }
}
