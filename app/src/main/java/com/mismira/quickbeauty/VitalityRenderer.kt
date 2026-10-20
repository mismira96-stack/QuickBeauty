package com.quickbeauty.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Makeup is painted in source space, then uses exactly the photo's mesh cells.
 * Cropped, grid-aligned layers avoid allocating another full-resolution photo. */
internal object VitalityRenderer {
    const val MAX_CHEEK_ALPHA = 85
    const val MAX_LIP_ALPHA = 75
    const val CHEEK_RGB = 0xE0829D
    const val LIP_RGB = 0xD94A78

    class Layer(val bitmap: Bitmap, val firstCol: Int, val firstRow: Int,
                val cols: Int, val rows: Int, val meshW: Int, val meshH: Int,
                val imageW: Int, val imageH: Int) {
        val bounds get() = RectF(firstCol * imageW.toFloat() / meshW,
            firstRow * imageH.toFloat() / meshH,
            (firstCol + cols) * imageW.toFloat() / meshW,
            (firstRow + rows) * imageH.toFloat() / meshH)

        fun vertices(photoVertices: FloatArray): FloatArray {
            require(photoVertices.size == (meshW + 1) * (meshH + 1) * 2)
            val result = FloatArray((cols + 1) * (rows + 1) * 2)
            for (r in 0..rows) for (c in 0..cols) {
                val src = ((firstRow + r) * (meshW + 1) + firstCol + c) * 2
                val dst = (r * (cols + 1) + c) * 2
                result[dst] = photoVertices[src]
                result[dst + 1] = photoVertices[src + 1]
            }
            return result
        }
    }

    fun build(landmarks: BeautyLandmarks?, params: BeautyAdjustParams,
              faceParams: Map<Int, BeautyAdjustParams>,
              meshW: Int = BeautyFilterEngine.MESH_W,
              meshH: Int = BeautyFilterEngine.MESH_H): List<Layer> {
        if (landmarks == null || landmarks.imageWidth <= 0 || landmarks.imageHeight <= 0) return emptyList()
        val faces = landmarks.allFaces.ifEmpty {
            if (!landmarks.hasFace) return emptyList()
            listOf(BeautyLandmarks.FaceInfo(index = landmarks.selectedFaceIndex).apply {
                bounds.set(landmarks.faceBounds.left, landmarks.faceBounds.top, landmarks.faceBounds.right, landmarks.faceBounds.bottom)
                leftCheek.set(landmarks.leftCheek.x, landmarks.leftCheek.y)
                rightCheek.set(landmarks.rightCheek.x, landmarks.rightCheek.y)
                noseBase.set(landmarks.noseBase.x, landmarks.noseBase.y)
                upperLipPoints.addAll(landmarks.upperLipPoints)
                lowerLipPoints.addAll(landmarks.lowerLipPoints)
            })
        }
        val layers = mutableListOf<Layer>()
        try {
            for (face in faces) {
                val amount = faceParams[face.index]?.vitality
                    ?: if (face.index == landmarks.selectedFaceIndex) params.vitality else 0
                if (amount <= 0) continue
                val b = face.bounds
                if (b.width() <= 0f || b.height() <= 0f ||
                    !listOf(b.left, b.top, b.right, b.bottom).all { it.isFinite() }) continue
                val w = landmarks.imageWidth
                val h = landmarks.imageHeight
                val c0 = floor(b.left / w * meshW).toInt().coerceIn(0, meshW - 1)
                val r0 = floor(b.top / h * meshH).toInt().coerceIn(0, meshH - 1)
                val c1 = ceil(b.right / w * meshW).toInt().coerceIn(c0 + 1, meshW)
                val r1 = ceil(b.bottom / h * meshH).toInt().coerceIn(r0 + 1, meshH)
                val left = c0 * w.toFloat() / meshW
                val top = r0 * h.toFloat() / meshH
                val spanW = (c1 - c0) * w.toFloat() / meshW
                val spanH = (r1 - r0) * h.toFloat() / meshH
                val bitmap = Bitmap.createBitmap(ceil(spanW).toInt(), ceil(spanH).toInt(), Bitmap.Config.ARGB_8888)
                val layer = Layer(bitmap, c0, r0, c1 - c0, r1 - r0, meshW, meshH, w, h)
                layers.add(layer)
                val canvas = Canvas(bitmap)
                canvas.scale(bitmap.width / spanW, bitmap.height / spanH)
                canvas.translate(-left, -top)
                // Exclude other detected faces before painting, so protection warps too.
                for (other in faces) if (other.index != face.index) {
                    val ob = other.bounds
                    canvas.clipOutRect(ob.left - ob.width() * .03f, ob.top - ob.height() * .03f,
                        ob.right + ob.width() * .03f, ob.bottom + ob.height() * .03f)
                }
                paintFace(canvas, face, amount.coerceIn(0, 100))
            }
            return layers
        } catch (t: Throwable) {
            recycle(layers)
            throw t
        }
    }

    private fun paintFace(canvas: Canvas, face: BeautyLandmarks.FaceInfo, amount: Int) {
        val b = face.bounds
        val fw = b.width()
        val fh = b.height()
        val factor = amount / 100f
        val skin = Path().apply { addOval(RectF(b.left, b.top, b.right, b.bottom), Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(skin)
        // A conservative lower-face window; not a semantic skin segmentation mask.
        canvas.clipRect(b.left, b.top + fh * .42f, b.right, b.top + fh * .91f)
        val radius = fw * .24f
        val alpha = (factor * MAX_CHEEK_ALPHA).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        if (radius > 2f && alpha > 0) for (cheek in listOf(face.leftCheek, face.rightCheek)) {
            if (!cheek.x.isFinite() || !cheek.y.isFinite()) continue
            val x = cheek.x + (face.noseBase.x - cheek.x) * .25f
            val y = cheek.y + (face.noseBase.y - cheek.y) * .15f
            paint.shader = RadialGradient(x, y, radius,
                intArrayOf((alpha shl 24) or CHEEK_RGB, ((alpha * .78f).toInt() shl 24) or CHEEK_RGB,
                    ((alpha * .32f).toInt() shl 24) or CHEEK_RGB, CHEEK_RGB),
                floatArrayOf(0f, .35f, .70f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
        }
        canvas.restore()

        // No guessed ellipses when lip contours are unavailable. Clip the blur to
        // each actual lip polygon so teeth and surrounding skin stay untouched.
        for ((points, extra) in listOf(face.upperLipPoints to false, face.lowerLipPoints to true)) {
            if (!validLip(points, b)) continue
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            canvas.save()
            canvas.clipPath(path)
            paint.shader = null
            paint.color = ((factor * MAX_LIP_ALPHA).toInt() shl 24) or LIP_RGB
            paint.maskFilter = BlurMaskFilter(max(1.5f, fw * .012f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, paint)
            if (extra) {
                paint.color = ((factor * MAX_LIP_ALPHA * .35f).toInt() shl 24) or 0xC44169
                canvas.drawPath(path, paint)
            }
            canvas.restore()
        }
    }

    private fun validLip(points: List<BeautyLandmarks.Point>, b: BeautyLandmarks.Rect): Boolean {
        if (points.size < 4 || points.any { !it.x.isFinite() || !it.y.isFinite() ||
                it.x !in b.left..b.right || it.y !in b.top..b.bottom }) return false
        var twiceArea = 0f
        for (i in points.indices) {
            val a = points[i]; val next = points[(i + 1) % points.size]
            twiceArea += a.x * next.y - next.x * a.y
        }
        return kotlin.math.abs(twiceArea) > b.width() * b.height() * .00002f
    }

    fun draw(canvas: Canvas, layers: List<Layer>, vertices: FloatArray?) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (layer in layers) {
            if (vertices == null) canvas.drawBitmap(layer.bitmap, null, layer.bounds, paint)
            else canvas.drawBitmapMesh(layer.bitmap, layer.cols, layer.rows, layer.vertices(vertices), 0, null, 0, paint)
        }
    }

    fun recycle(layers: List<Layer>) { layers.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() } }
}
