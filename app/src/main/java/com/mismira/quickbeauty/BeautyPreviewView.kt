package com.quickbeauty.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import kotlin.math.max

class BeautyPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var landmarks: BeautyLandmarks? = null
    private var params = BeautyAdjustParams()
    private var faceParamsByIndex: Map<Int, BeautyAdjustParams> = emptyMap()
    private var showOriginal = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destRect = RectF()

    private var cachedVerts: FloatArray? = null
    private var transformedVerts: FloatArray? = null
    private var vertsDirty = true
    private var vitalityDirty = true
    private var vitalityLayers: List<VitalityRenderer.Layer> = emptyList()
    private var lastLeft = -1f
    private var lastTop = -1f
    private var lastScale = -1f

    private val viewport = PreviewViewport()
    var onFaceTapped: ((Int) -> Unit)? = null
    var onCompareChanged: ((Boolean) -> Unit)? = null
    private var multiTouch = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            endComparison()
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            findFaceAt(e.x, e.y)?.let { onFaceTapped?.invoke(it) }
            performClick()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            endComparison()
            viewport.zoomAt(if (viewport.zoom > 1.01f) 1f / viewport.zoom else 3f, e.x, e.y)
            invalidate()
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            endComparison()
            viewport.pan(-distanceX, -distanceY)
            invalidate()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!multiTouch && sourceBitmap != null) {
                setShowOriginal(true)
                onCompareChanged?.invoke(true)
            }
        }
    })

    private fun endComparison() {
        setShowOriginal(false)
        onCompareChanged?.invoke(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sourceBitmap == null) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) multiTouch = false
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true
            endComparison()
            MotionEvent.obtain(event).also {
                it.action = MotionEvent.ACTION_CANCEL
                gestures.onTouchEvent(it)
                it.recycle()
            }
        }
        if (!multiTouch) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) endComparison()
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    init {
        setBackgroundColor(Color.BLACK)
        // Adreno Vulkan 드라이버의 drawBitmapMesh 셰이더 컴파일 결함 방지 (Skia CPU 초고속 60fps 연산)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSource(bitmap: Bitmap?, landmarks: BeautyLandmarks?) {
        if (this.sourceBitmap !== bitmap) {
            viewport.reset()
            endComparison()
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            gestures.onTouchEvent(cancel)
            scaleDetector.onTouchEvent(cancel)
            cancel.recycle()
        }
        this.sourceBitmap = bitmap
        this.landmarks = landmarks
        // 새 사진을 열면 포커스 링은 숨기고, 얼굴을 직접 탭했을 때만 표시한다.
        handler?.removeCallbacks(fadeOutRunnable)
        focusAnimator?.cancel()
        focusRingAlpha = 0f
        this.vertsDirty = true
        clearVitalityLayers()
        lastScale = -1f
        lastLeft = -1f
        lastTop = -1f
        invalidate()
    }

    fun setParams(params: BeautyAdjustParams) {
        if (this.params.vitality != params.vitality) vitalityDirty = true
        this.params = params.copy()
        this.vertsDirty = true
        invalidate()
    }

    fun setFaceParams(faceParams: Map<Int, BeautyAdjustParams>) {
        if (this.faceParamsByIndex.mapValues { it.value.vitality } != faceParams.mapValues { it.value.vitality }) vitalityDirty = true
        this.faceParamsByIndex = faceParams.mapValues { it.value.copy() }
        this.vertsDirty = true
        invalidate()
    }

    fun setShowOriginal(show: Boolean) {
        if (this.showOriginal != show) {
            this.showOriginal = show
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return
        if (bmp.isRecycled) return

        val viewW = width
        val viewH = height
        if (viewW <= 0 || viewH <= 0) return

        val bmpW = bmp.width
        val bmpH = bmp.height

        viewport.configure(viewW.toFloat(), viewH.toFloat(), bmpW.toFloat(), bmpH.toFloat())
        val scale = viewport.scale
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = viewport.left
        val top = viewport.top
        destRect.set(left, top, left + drawW, top + drawH)

        val geometryChanged = left != lastLeft || top != lastTop || scale != lastScale
        lastLeft = left
        lastTop = top
        lastScale = scale

        val currentLandmarks = landmarks
        val hasFaceEdits = faceParamsByIndex.values.any { it.faceSize > 0 || it.chinSlim > 0 || it.faceLength > 0 || it.vitality > 0 }
        if (showOriginal || (params.isDefault() && !hasFaceEdits) || currentLandmarks == null) {
            paint.colorFilter = null
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            canvas.restore()

            if (!showOriginal && focusRingAlpha > 0f && currentLandmarks != null && currentLandmarks.allFaces.isNotEmpty()) {
                drawFaceIndicators(canvas, currentLandmarks, left, top, scale)
            }
            return
        }

        if (params.coolTone > 0) {
            val cm = BeautyFilterEngine.createCoolToneMatrix(params.coolTone)
            paint.colorFilter = ColorMatrixColorFilter(cm)
        } else {
            paint.colorFilter = null
        }

        val hasWarpEdits = faceParamsByIndex.values.any { it.faceSize > 0 || it.chinSlim > 0 || it.faceLength > 0 }
        val needWarp = BeautyFilterEngine.hasSupportedWarp(currentLandmarks, params) || hasWarpEdits
        if (!needWarp) {
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            canvas.restore()
        } else {
            var needsTransform = geometryChanged
            if (vertsDirty || cachedVerts == null) {
                cachedVerts = BeautyFilterEngine.computeWarpedVertices(bmpW, bmpH, currentLandmarks, params, faceParamsByIndex = faceParamsByIndex)
                vertsDirty = false
                needsTransform = true
            }

            val currentCached = cachedVerts
            if (currentCached != null) {
                if (needsTransform || transformedVerts == null || transformedVerts?.size != currentCached.size) {
                    val count = currentCached.size
                    if (transformedVerts == null || transformedVerts?.size != count) {
                        transformedVerts = FloatArray(count)
                    }
                    val trans = transformedVerts!!
                    for (i in 0 until count step 2) {
                        trans[i] = left + currentCached[i] * scale
                        trans[i + 1] = top + currentCached[i + 1] * scale
                    }
                    lastLeft = left
                    lastTop = top
                    lastScale = scale
                }

                canvas.drawBitmapMesh(
                    bmp,
                    BeautyFilterEngine.MESH_W,
                    BeautyFilterEngine.MESH_H,
                    transformedVerts!!,
                    0,
                    null,
                    0,
                    paint
                )
            }
        }

        val hasVitality = params.vitality > 0 || faceParamsByIndex.values.any { it.vitality > 0 }
        if (hasVitality) {
            if (vitalityDirty) {
                clearVitalityLayers()
                vitalityLayers = VitalityRenderer.build(currentLandmarks, params, faceParamsByIndex)
                vitalityDirty = false
            }
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            VitalityRenderer.draw(canvas, vitalityLayers, if (needWarp) cachedVerts else null)
            canvas.restore()
        }

        // 얼굴 감지 시 은은한 얼굴 포커스 링 오버레이 표시
        if (!showOriginal && focusRingAlpha > 0f && currentLandmarks.allFaces.isNotEmpty()) {
            drawFaceIndicators(canvas, currentLandmarks, left, top, scale)
        }
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var focusRingAlpha = 0f
    private var focusAnimator: android.animation.ValueAnimator? = null
    private val fadeOutRunnable = Runnable {
        animateFocusRing(0f)
    }

    fun showFocusIndicator() {
        handler?.removeCallbacks(fadeOutRunnable)
        animateFocusRing(1f)
        handler?.postDelayed(fadeOutRunnable, 2500)
    }

    private fun animateFocusRing(target: Float) {
        focusAnimator?.cancel()
        focusAnimator = android.animation.ValueAnimator.ofFloat(focusRingAlpha, target).apply {
            duration = 350
            addUpdateListener {
                focusRingAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun clearVitalityLayers() {
        VitalityRenderer.recycle(vitalityLayers)
        vitalityLayers = emptyList()
        vitalityDirty = true
    }

    override fun onDetachedFromWindow() {
        handler?.removeCallbacks(fadeOutRunnable)
        focusAnimator?.cancel()
        clearVitalityLayers()
        super.onDetachedFromWindow()
    }

    private fun selectionTargets(): List<FaceSelection.Target> {
        val lm = landmarks ?: return emptyList()
        if (lastScale <= 0f) return emptyList()
        val needWarp = BeautyFilterEngine.hasSupportedWarp(lm, params) ||
            faceParamsByIndex.values.any { it.faceSize > 0 || it.chinSlim > 0 || it.faceLength > 0 }
        val vertices = if (needWarp) cachedVerts else null
        return lm.allFaces.map { face ->
            val b = face.bounds
            val points = listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).map { (x, y) ->
                BeautyFilterEngine.warpPoint(x, y, vertices, BeautyFilterEngine.MESH_W, BeautyFilterEngine.MESH_H,
                    lm.imageWidth.toFloat(), lm.imageHeight.toFloat())
            }
            FaceSelection.Target(face.index, BeautyLandmarks.Rect(
                lastLeft + points.minOf { it.x } * lastScale, lastTop + points.minOf { it.y } * lastScale,
                lastLeft + points.maxOf { it.x } * lastScale, lastTop + points.maxOf { it.y } * lastScale))
        }
    }

    private fun drawFaceIndicators(canvas: Canvas, landmarks: BeautyLandmarks, left: Float, top: Float, scale: Float) {
        val face = selectionTargets().firstOrNull { it.index == landmarks.selectedFaceIndex } ?: return
        val b = face.bounds
        indicatorPaint.color = Color.parseColor("#3B82F6")
        indicatorPaint.alpha = (focusRingAlpha * 255).toInt().coerceIn(0, 255)
        indicatorPaint.strokeWidth = 3f * resources.displayMetrics.density
        val radius = (max(b.width(), b.height()) * .72f).coerceAtLeast(24f * resources.displayMetrics.density)
        canvas.drawCircle(b.centerX(), b.centerY(), radius, indicatorPaint)
    }

    /** Prefer the actual face rectangle over another face's expanded margin. */
    fun findFaceAt(viewX: Float, viewY: Float): Int? {
        val lm = landmarks ?: return null
        return FaceSelection.find(viewX, viewY, selectionTargets(), lm.selectedFaceIndex,
            32f * resources.displayMetrics.density)
    }
}
