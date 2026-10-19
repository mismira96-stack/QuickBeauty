package com.quickbeauty.app

import kotlin.math.min
import kotlin.math.max

/** Preview-only transform; exports and edit strength are unaffected. */
internal class PreviewViewport {
    var zoom = 1f
        private set
    private var offsetX = 0f
    private var offsetY = 0f
    private var width = 0f
    private var height = 0f
    private var imageWidth = 1f
    private var imageHeight = 1f
    val scale get() = min(width / imageWidth, height / imageHeight) * zoom
    val left get() = (width - imageWidth * scale) / 2f + offsetX
    val top get() = (height - imageHeight * scale) / 2f + offsetY

    fun configure(w: Float, h: Float, iw: Float, ih: Float) {
        width = w; height = h; imageWidth = iw; imageHeight = ih
        clamp()
    }
    fun reset() { zoom = 1f; offsetX = 0f; offsetY = 0f }
    fun zoomAt(factor: Float, x: Float, y: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        val next = (zoom * factor).coerceIn(1f, 6f)
        val ratio = next / zoom
        offsetX = (x - width / 2f) * (1f - ratio) + offsetX * ratio
        offsetY = (y - height / 2f) * (1f - ratio) + offsetY * ratio
        zoom = next
        clamp()
    }
    fun pan(dx: Float, dy: Float) { offsetX += dx; offsetY += dy; clamp() }
    private fun clamp() {
        val xLimit = max(0f, (imageWidth * scale - width) / 2f)
        val yLimit = max(0f, (imageHeight * scale - height) / 2f)
        offsetX = offsetX.coerceIn(-xLimit, xLimit)
        offsetY = offsetY.coerceIn(-yLimit, yLimit)
    }
}
