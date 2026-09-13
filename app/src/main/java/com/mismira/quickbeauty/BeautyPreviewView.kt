package com.mismira.quickbeauty

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class BeautyPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var landmarks: BeautyLandmarks? = null
    private var params = BeautyAdjustParams()
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
        this.vertsDirty = true
        invalidate()
    }

    fun setParams(params: BeautyAdjustParams) {
        this.params = params.copy()
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

        val currentLandmarks = landmarks
        if (showOriginal || params.isDefault() || currentLandmarks == null) {
            paint.colorFilter = null
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            canvas.restore()
            return
        }

        if (params.coolTone > 0) {
            val cm = BeautyFilterEngine.createCoolToneMatrix(params.coolTone)
            paint.colorFilter = ColorMatrixColorFilter(cm)
        } else {
            paint.colorFilter = null
        }

        val needWarp = params.faceSlim > 0 || params.chinSlim > 0 || params.bodySlim > 0
        if (!needWarp) {
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            canvas.restore()
        } else {
            var geometryChanged = left != lastLeft || top != lastTop || scale != lastScale
            if (vertsDirty || cachedVerts == null) {
                cachedVerts = BeautyFilterEngine.computeWarpedVertices(bmpW, bmpH, currentLandmarks, params)
                vertsDirty = false
                geometryChanged = true
            }

            val currentCached = cachedVerts
            if (currentCached != null) {
                if (geometryChanged || transformedVerts == null || transformedVerts?.size != currentCached.size) {
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
    }
}
