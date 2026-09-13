package com.mismira.quickbeauty

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object BeautyFilterEngine {
    const val MESH_W = 40
    const val MESH_H = 40

    fun getCoolToneMatrixArray(coolTone: Int): FloatArray {
        if (coolTone <= 0) {
            return floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        val f = coolTone / 100.0f
        val rScale = 1.0f - f * 0.01f
        val gScale = 1.0f - f * 0.07f
        val bScale = 1.0f + f * 0.16f
        val brightness = f * 20.0f

        return floatArrayOf(
            rScale, 0f, 0f, 0f, brightness * 0.85f,
            0f, gScale, 0f, 0f, brightness * 0.75f,
            0f, 0f, bScale, 0f, brightness * 1.25f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    fun createCoolToneMatrix(coolTone: Int): ColorMatrix {
        return ColorMatrix(getCoolToneMatrixArray(coolTone))
    }

    fun computeWarpedVertices(
        width: Int,
        height: Int,
        landmarks: BeautyLandmarks?,
        params: BeautyAdjustParams
    ): FloatArray {
        val totalVerts = (MESH_W + 1) * (MESH_H + 1)
        val verts = FloatArray(totalVerts * 2)

        val faceSlimFactor = (params.faceSlim / 100.0f) * 0.28f
        val chinSlimFactor = (params.chinSlim / 100.0f) * 0.35f
        val bodySlimFactor = (params.bodySlim / 100.0f) * 0.25f

        val hasFace = landmarks?.hasFace == true
        val hasBody = landmarks?.hasBody == true

        val fc = landmarks?.faceCenter
        var faceRadius = if (landmarks != null) {
            max(landmarks.faceBounds.width(), landmarks.faceBounds.height()) * 0.55f
        } else {
            0f
        }
        if (faceRadius <= 10f) {
            faceRadius = min(width.toFloat(), height.toFloat()) * 0.2f
        }

        val chinY = landmarks?.chinPoint?.y ?: 0f
        val chinX = landmarks?.chinPoint?.x ?: 0f

        val bodyCenterY = landmarks?.bodyCenterY ?: 0f
        val bodyCenterX = landmarks?.bodyBounds?.centerX() ?: 0f
        val bodyTopY = landmarks?.bodyTopY ?: 0f
        val bodyBottomY = landmarks?.bodyBottomY ?: 0f
        val bodyHalfHeight = max(20f, (bodyBottomY - bodyTopY) * 0.5f)
        val bodyHalfWidth = max(20f, (landmarks?.bodyBounds?.width() ?: 0f) * 0.6f)

        var index = 0
        for (r in 0..MESH_H) {
            val origY = r.toFloat() * height / MESH_H
            for (c in 0..MESH_W) {
                val origX = c.toFloat() * width / MESH_W
                var currX = origX
                var currY = origY

                // 1. Face Slim
                if (faceSlimFactor > 0.001f && hasFace && fc != null) {
                    val dx = origX - fc.x
                    val dy = origY - fc.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val maxR = faceRadius * 1.5f
                    if (dist < maxR && dist > 1f) {
                        val norm = dist / maxR
                        val weight = (1.0f - norm) * (1.0f - norm) * faceSlimFactor
                        currX -= dx * weight
                        currY -= dy * weight * 0.8f
                    }
                }

                // 2. Chin Slim (V-Line Jaw & Chin Lift)
                if (chinSlimFactor > 0.001f && hasFace && fc != null) {
                    val dx = origX - chinX
                    val dy = origY - chinY
                    val maxDx = faceRadius * 1.2f
                    val maxUpperY = max(20f, (chinY - fc.y) * 0.95f)
                    val maxLowerY = max(20f, faceRadius * 0.45f)

                    var yWeight = 0f
                    if (dy < 0 && -dy < maxUpperY) {
                        val yNorm = (-dy) / maxUpperY
                        yWeight = (1.0f - yNorm) * (1.0f - yNorm)
                    } else if (dy >= 0 && dy < maxLowerY) {
                        val yNorm = dy / maxLowerY
                        yWeight = (1.0f - yNorm) * (1.0f - yNorm)
                    }

                    if (yWeight > 0f) {
                        val absDx = abs(dx)
                        if (absDx < maxDx) {
                            val xNorm = absDx / maxDx
                            val xWeight = (1.0f - xNorm) * (1.0f - xNorm)
                            val totalWeight = yWeight * xWeight * chinSlimFactor

                            currX -= dx * totalWeight * 0.85f
                            if (dy > 0) {
                                currY -= dy * totalWeight * 0.35f
                            }
                        }
                    }
                }

                // 3. Body Slim
                if (bodySlimFactor > 0.001f && hasBody) {
                    if (origY in bodyTopY..bodyBottomY) {
                        val dy = abs(origY - bodyCenterY)
                        var yWeight = 1.0f - (dy / bodyHalfHeight)
                        if (yWeight > 0f) {
                            yWeight = sin(yWeight * Math.PI * 0.5).toFloat()
                            val dx = origX - bodyCenterX
                            val absDx = abs(dx)
                            val maxBodyW = bodyHalfWidth * 2.0f
                            if (absDx < maxBodyW) {
                                val xNorm = absDx / maxBodyW
                                val xWeight = (1.0f - xNorm) * (1.0f - xNorm)
                                val totalWeight = yWeight * xWeight * bodySlimFactor
                                currX -= dx * totalWeight
                            }
                        }
                    }
                }

                verts[index++] = currX
                verts[index++] = currY
            }
        }
        return verts
    }

    fun process(source: Bitmap?, landmarks: BeautyLandmarks?, params: BeautyAdjustParams): Bitmap? {
        if (source == null || source.isRecycled) return null

        val w = source.width
        val h = source.height

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (params.coolTone > 0) {
            val cm = createCoolToneMatrix(params.coolTone)
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }

        val needWarp = (params.faceSlim > 0 || params.chinSlim > 0 || params.bodySlim > 0) && landmarks != null
        if (needWarp && landmarks != null) {
            val scaledLandmarks = if (landmarks.imageWidth == w && landmarks.imageHeight == h) {
                landmarks
            } else {
                landmarks.scaleTo(w, h)
            }
            val verts = computeWarpedVertices(w, h, scaledLandmarks, params)
            canvas.drawBitmapMesh(source, MESH_W, MESH_H, verts, 0, null, 0, paint)
        } else {
            canvas.drawBitmap(source, 0f, 0f, paint)
        }

        return output
    }
}
