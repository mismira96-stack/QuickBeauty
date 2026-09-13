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
        params: BeautyAdjustParams,
        meshW: Int = MESH_W,
        meshH: Int = MESH_H
    ): FloatArray {
        val totalVerts = (meshW + 1) * (meshH + 1)
        val verts = FloatArray(totalVerts * 2)

        val faceSlimFactor = (params.faceSlim / 100.0f).coerceIn(0f, 1f)
        val faceLengthFactor = (params.faceLength / 100.0f).coerceIn(0f, 1f)
        val bodySlimFactor = (params.bodySlim / 100.0f).coerceIn(0f, 1f)

        val hasFace = landmarks?.hasFace == true
        val hasBody = landmarks?.hasBody == true

        val fc = landmarks?.faceCenter
        val fcX = fc?.x ?: (width * 0.5f)
        val fcY = fc?.y ?: (height * 0.35f)

        val fw = max(20f, landmarks?.faceBounds?.width() ?: (width * 0.35f))
        val fh = max(20f, landmarks?.faceBounds?.height() ?: (height * 0.35f))

        // 세로 위치 기준점
        val chinY = landmarks?.chinPoint?.y ?: (fcY + fh * 0.45f)
        val mouthY = landmarks?.mouthPoint?.y ?: (fcY + fh * 0.28f)
        val noseBaseY = landmarks?.noseBase?.y ?: (fcY + fh * 0.08f)
        val neckY = chinY + fh * 0.35f

        // 1. 얼굴 가로 폭 슬리밍 (볼/사각턱 축소) 파라미터
        val cheekTopY = fcY - fh * 0.08f
        val cheekBotY = chinY
        val ySpan = max(20f, (cheekBotY + fh * 0.12f) - (cheekTopY - fh * 0.12f))
        val yStart = cheekTopY - fh * 0.12f
        val innerProtectW = fw * 0.18f
        val peakCheekW = fw * 0.46f
        val outerFalloffW = fw * 0.82f
        val maxCheekPush = fw * 0.065f * faceSlimFactor

        // 2. 얼굴 세로 길이 축소 (하관 리프팅 / 긴 얼굴 단축) 파라미터
        val maxChinLift = fh * 0.075f * faceLengthFactor
        val lengthInnerW = fw * 0.40f
        val lengthOuterW = fw * 0.75f
        val chinToMouthSpan = max(10f, chinY - mouthY)
        val mouthToNoseSpan = max(10f, mouthY - noseBaseY)
        val neckSpan = max(10f, neckY - chinY)
        val foreheadTopY = fcY - fh * 0.50f
        val foreheadSpan = max(10f, fh * 0.30f)

        // 3. 몸매 슬림 파라미터
        val bodyCenterY = landmarks?.bodyCenterY ?: 0f
        val bodyCenterX = landmarks?.bodyBounds?.centerX() ?: 0f
        val bodyTopY = landmarks?.bodyTopY ?: 0f
        val bodyBottomY = landmarks?.bodyBottomY ?: 0f
        val bodyHalfHeight = max(20f, (bodyBottomY - bodyTopY) * 0.5f)
        val bodyHalfWidth = max(20f, (landmarks?.bodyBounds?.width() ?: 0f) * 0.6f)

        var index = 0
        for (r in 0..meshH) {
            val origY = r.toFloat() * height / meshH
            for (c in 0..meshW) {
                val origX = c.toFloat() * width / meshW
                var currX = origX
                var currY = origY

                // ==========================================
                // 1. 얼굴 가로 폭 축소 (Face Slim)
                // 눈·코·입 중심 영역 100% 보존, 옆볼/사각턱만 부드럽게 축소
                // ==========================================
                if (faceSlimFactor > 0.001f && hasFace) {
                    val yNorm = (origY - yStart) / ySpan
                    if (yNorm in 0f..1f) {
                        val yWeight = sin(yNorm * Math.PI.toFloat())
                        val dx = origX - fcX
                        val absDx = abs(dx)

                        val xWeight: Float = when {
                            absDx <= innerProtectW -> 0f
                            absDx < peakCheekW -> {
                                val t = (absDx - innerProtectW) / (peakCheekW - innerProtectW)
                                t * t * (3f - 2f * t)
                            }
                            absDx <= outerFalloffW -> {
                                val t = (outerFalloffW - absDx) / (outerFalloffW - peakCheekW)
                                t * t * (3f - 2f * t)
                            }
                            else -> 0f
                        }

                        if (xWeight > 0f && yWeight > 0f) {
                            val push = maxCheekPush * yWeight * xWeight
                            if (dx < 0f) {
                                currX += push // 좌측 볼 -> 안쪽으로 수축
                            } else {
                                currX -= push // 우측 볼 -> 안쪽으로 수축
                            }
                        }
                    }
                }

                // ==========================================
                // 2. 얼굴 세로 길이 축소 (Face Length / Chin Lift)
                // 턱 끝을 끌어올려 긴 하관/인중을 동안 비율로 단축
                // ==========================================
                if (faceLengthFactor > 0.001f && hasFace) {
                    val absDx = abs(origX - fcX)
                    val xWeight: Float = when {
                        absDx <= lengthInnerW -> 1f
                        absDx < lengthOuterW -> {
                            val t = (lengthOuterW - absDx) / (lengthOuterW - lengthInnerW)
                            t * t * (3f - 2f * t)
                        }
                        else -> 0f
                    }

                    if (xWeight > 0f) {
                        var liftRatio = 0f
                        var downRatio = 0f

                        when {
                            // A. 턱 끝 ~ 목: 턱끝에서 최대 리프팅, 목 쪽으로 자연스럽게 감쇠
                            origY >= chinY && origY <= neckY -> {
                                val t = (neckY - origY) / neckSpan
                                liftRatio = t * t * (3f - 2f * t)
                            }
                            // B. 입술 ~ 턱 끝: 턱 끝(1.0)에서 입술(0.25)로 감쇠하며 턱 길이 단축
                            origY in mouthY..chinY -> {
                                val t = (origY - mouthY) / chinToMouthSpan
                                liftRatio = 0.25f + 0.75f * (t * t * (3f - 2f * t))
                            }
                            // C. 코 밑 ~ 입술: 입술(0.25)에서 코 밑(0.0)으로 감쇠하며 인중 살짝 단축
                            origY in noseBaseY..mouthY -> {
                                val t = (origY - noseBaseY) / mouthToNoseSpan
                                liftRatio = 0.25f * (t * t * (3f - 2f * t))
                            }
                            // D. 이마 상단부: 위쪽에서 살짝 내려 전체적인 두상 밸런스 유지
                            origY < foreheadTopY && origY >= (foreheadTopY - foreheadSpan) -> {
                                val t = (origY - (foreheadTopY - foreheadSpan)) / foreheadSpan
                                downRatio = (1f - t) * 0.18f
                            }
                        }

                        if (liftRatio > 0f) {
                            currY -= maxChinLift * liftRatio * xWeight
                        } else if (downRatio > 0f) {
                            currY += maxChinLift * downRatio * xWeight
                        }
                    }
                }

                // ==========================================
                // 3. 몸매 슬림 (Body Slim)
                // 복부/허리 라인을 중앙으로 완만하게 축소
                // ==========================================
                if (bodySlimFactor > 0.001f && hasBody) {
                    if (origY in bodyTopY..bodyBottomY) {
                        val dy = abs(origY - bodyCenterY)
                        val yNorm = 1.0f - (dy / bodyHalfHeight)
                        if (yNorm > 0f) {
                            val yWeight = sin(yNorm * Math.PI.toFloat() * 0.5f)
                            val dx = origX - bodyCenterX
                            val absDx = abs(dx)
                            val maxBodyW = bodyHalfWidth * 2.0f
                            if (absDx < maxBodyW) {
                                val xNorm = (maxBodyW - absDx) / maxBodyW
                                val xWeight = xNorm * xNorm
                                val totalWeight = yWeight * xWeight * bodySlimFactor * 0.22f
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

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }

        if (params.coolTone > 0) {
            val cm = createCoolToneMatrix(params.coolTone)
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }

        val needWarp = (params.faceSlim > 0 || params.faceLength > 0 || params.bodySlim > 0) && landmarks != null
        if (needWarp) {
            val validLandmarks = landmarks!!
            val scaledLandmarks = if (validLandmarks.imageWidth == w && validLandmarks.imageHeight == h) {
                validLandmarks
            } else {
                validLandmarks.scaleTo(w, h)
            }
            // 고해상도 이미지일 경우 더 정밀한 60x60 메쉬 적용으로 초고화질 곡선 완벽 보존
            val meshW = if (w >= 2000 || h >= 2000) 60 else MESH_W
            val meshH = if (w >= 2000 || h >= 2000) 60 else MESH_H
            val verts = computeWarpedVertices(w, h, scaledLandmarks, params, meshW, meshH)
            canvas.drawBitmapMesh(source, meshW, meshH, verts, 0, null, 0, paint)
        } else {
            canvas.drawBitmap(source, 0f, 0f, paint)
        }

        return output
    }
}
