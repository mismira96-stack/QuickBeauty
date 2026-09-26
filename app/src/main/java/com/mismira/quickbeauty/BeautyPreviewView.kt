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
import kotlin.math.max
import kotlin.math.min

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
    private var lastLeft = -1f
    private var lastTop = -1f
    private var lastScale = -1f

    init {
        setBackgroundColor(Color.BLACK)
        // Adreno Vulkan 드라이버의 drawBitmapMesh 셰이더 컴파일 결함 방지 (Skia CPU 초고속 60fps 연산)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSource(bitmap: Bitmap?, landmarks: BeautyLandmarks?) {
        this.sourceBitmap = bitmap
        this.landmarks = landmarks
        // 새 사진을 열면 포커스 링은 숨기고, 얼굴을 직접 탭했을 때만 표시한다.
        handler?.removeCallbacks(fadeOutRunnable)
        focusRingAlpha = 0f
        this.vertsDirty = true
        invalidate()
    }

    fun setParams(params: BeautyAdjustParams) {
        this.params = params.copy()
        this.vertsDirty = true
        invalidate()
    }

    fun setFaceParams(faceParams: Map<Int, BeautyAdjustParams>) {
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

        val scale = min(viewW.toFloat() / bmpW, viewH.toFloat() / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = (viewW - drawW) * 0.5f
        val top = (viewH - drawH) * 0.5f
        destRect.set(left, top, left + drawW, top + drawH)

        val geometryChanged = left != lastLeft || top != lastTop || scale != lastScale
        lastLeft = left
        lastTop = top
        lastScale = scale

        val currentLandmarks = landmarks
        val hasFaceEdits = faceParamsByIndex.values.any { it.faceSize > 0 || it.chinSlim > 0 || it.faceLength > 0 }
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

        val needWarp = BeautyFilterEngine.hasSupportedWarp(currentLandmarks, params) || hasFaceEdits
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

        // 얼굴 감지 시 은은한 얼굴 포커스 링 오버레이 표시
        if (!showOriginal && focusRingAlpha > 0f && currentLandmarks.allFaces.isNotEmpty()) {
            drawFaceIndicators(canvas, currentLandmarks, left, top, scale)
        }
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var focusRingAlpha = 0f
    private val fadeOutRunnable = Runnable {
        animateFocusRing(0f)
    }

    fun showFocusIndicator() {
        handler?.removeCallbacks(fadeOutRunnable)
        animateFocusRing(1f)
        handler?.postDelayed(fadeOutRunnable, 2500)
    }

    private fun animateFocusRing(target: Float) {
        val animator = android.animation.ValueAnimator.ofFloat(focusRingAlpha, target).apply {
            duration = 350
            addUpdateListener {
                focusRingAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }

    private fun drawFaceIndicators(canvas: Canvas, landmarks: BeautyLandmarks, left: Float, top: Float, scale: Float) {
        val selectedIdx = landmarks.selectedFaceIndex
        val face = landmarks.allFaces.firstOrNull { it.index == selectedIdx } ?: return
        val cx = left + face.center.x * scale
        val cy = top + face.center.y * scale
        val r = (max(face.bounds.width(), face.bounds.height()) * 0.55f * scale).coerceAtLeast(30f)

        // 사용자가 탭한 현재 보정 대상 하나만 표시해 화면이 복잡해지지 않도록 한다.
        val baseAlpha = (focusRingAlpha * 255).toInt().coerceIn(0, 255)
        indicatorPaint.color = Color.parseColor("#3B82F6")
        indicatorPaint.alpha = baseAlpha
        indicatorPaint.strokeWidth = 3f * resources.displayMetrics.density
        canvas.drawCircle(cx, cy, r, indicatorPaint)
    }

    /**
     * 뷰 터치 좌표(viewX, viewY)에 위치한 얼굴 인덱스 탐색
     * 화면 픽셀(dp) 기준의 거리 계산과 넉넉한 터치 영역(최소 56dp)을 제공하여
     * 원거리/소형 인물이라도 손가락 터치 시 100% 안정적으로 인식
     */
    fun findFaceAt(viewX: Float, viewY: Float): Int? {
        val lm = landmarks ?: return null
        if (lm.allFaces.isEmpty()) return null

        val scale = lastScale
        if (scale <= 0f) return null

        val density = resources.displayMetrics.density
        val minHitRadiusPx = 56f * density // 화면 기준 최소 56dp (손가락 터치 반경)

        var closestIndex: Int? = null
        var minDistance = Float.MAX_VALUE

        for (face in lm.allFaces) {
            val fcViewX = lastLeft + face.center.x * scale
            val fcViewY = lastTop + face.center.y * scale

            val dx = viewX - fcViewX
            val dy = viewY - fcViewY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            val faceRadiusView = max(face.bounds.width(), face.bounds.height()) * 0.5f * scale
            val hitThreshold = max(minHitRadiusPx, faceRadiusView * 1.6f)

            if (dist <= hitThreshold && dist < minDistance) {
                minDistance = dist
                closestIndex = face.index
            }
        }
        return closestIndex
    }
}

