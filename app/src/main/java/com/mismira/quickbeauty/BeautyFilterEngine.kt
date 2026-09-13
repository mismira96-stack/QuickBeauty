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

        val faceSizeFactor = (params.faceSize / 100.0f).coerceIn(0f, 1f)
        val chinSlimFactor = (params.chinSlim / 100.0f).coerceIn(0f, 1f)
        val faceLengthFactor = (params.faceLength / 100.0f).coerceIn(0f, 1f)
        val bodySlimFactor = (params.bodySlim / 100.0f).coerceIn(0f, 1f)

        val hasFace = landmarks?.hasFace == true
        val hasBody = landmarks?.hasBody == true

        val fc = landmarks?.faceCenter
        val fcX = fc?.x ?: (width * 0.5f)
        val fcY = fc?.y ?: (height * 0.35f)

        val fw = max(20f, landmarks?.faceBounds?.width() ?: (width * 0.35f))
        val fh = max(20f, landmarks?.faceBounds?.height() ?: (height * 0.35f))

        // 1. 얼굴 전체 크기 축소 (소두 효과 - X/Y 비율 유지 전체 축소) 파라미터
        val headRadius = max(fw, fh) * 0.70f
        val headInnerR = headRadius * 0.60f
        val headOuterR = headRadius * 1.35f
        val maxHeadScale = 0.085f * faceSizeFactor

        // 2. 턱선 V라인 슬림 파라미터
        val chinY = landmarks?.chinPoint?.y ?: (fcY + fh * 0.45f)
        val mouthY = landmarks?.mouthPoint?.y ?: (fcY + fh * 0.28f)
        val noseBaseY = landmarks?.noseBase?.y ?: (fcY + fh * 0.08f)
        val neckY = chinY + fh * 0.35f

        val jawTopY = mouthY - fh * 0.05f
        val jawBotY = chinY
        val jawSpan = max(20f, (jawBotY + fh * 0.10f) - (jawTopY - fh * 0.05f))
        val jawYStart = jawTopY - fh * 0.05f
        val jawInnerProtectW = fw * 0.15f
        val jawPeakW = fw * 0.45f
        val jawOuterW = fw * 0.78f
        val maxJawPush = fw * 0.060f * chinSlimFactor

        // 3. 얼굴 세로 길이 축소 (하관 리프팅 / 긴 얼굴 단축) 파라미터
        val maxChinLift = fh * 0.075f * faceLengthFactor
        val lengthInnerW = fw * 0.40f
        val lengthOuterW = fw * 0.75f
        val chinToMouthSpan = max(10f, chinY - mouthY)
        val mouthToNoseSpan = max(10f, mouthY - noseBaseY)
        val neckSpan = max(10f, neckY - chinY)
        val foreheadTopY = fcY - fh * 0.50f
        val foreheadSpan = max(10f, fh * 0.30f)

        // 4. 몸매 슬림 파라미터
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
                // 1. 얼굴 전체 크기 축소 (Face Size - 소두)
                // 두상/얼굴 전체를 X, Y 균일하게 축소하여 어깨 대비 얼굴 비율 축소
                // ==========================================
                if (faceSizeFactor > 0.001f && hasFace) {
                    val dx = origX - fcX
                    val dy = origY - fcY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < headOuterR) {
                        val w = if (dist <= headInnerR) {
                            1.0f
                        } else {
                            val t = (headOuterR - dist) / (headOuterR - headInnerR)
                            t * t * (3f - 2f * t)
                        }
                        val s = maxHeadScale * w
                        currX -= dx * s
                        currY -= dy * s * 0.90f
                    }
                }

                // ==========================================
                // 2. 턱선 V라인 슬림 (Jawline V-Line)
                // 양 볼살 및 사각턱 라인을 V라인으로 갸름하게 압축
                // ==========================================
                if (chinSlimFactor > 0.001f && hasFace) {
                    val yNorm = (origY - jawYStart) / jawSpan
                    if (yNorm in 0f..1f) {
                        val yWeight = sin(yNorm * Math.PI.toFloat())
                        val dx = origX - fcX
                        val absDx = abs(dx)

                        val xWeight: Float = when {
                            absDx <= jawInnerProtectW -> 0f
                            absDx < jawPeakW -> {
                                val t = (absDx - jawInnerProtectW) / (jawPeakW - jawInnerProtectW)
                                t * t * (3f - 2f * t)
                            }
                            absDx <= jawOuterW -> {
                                val t = (jawOuterW - absDx) / (jawOuterW - jawPeakW)
                                t * t * (3f - 2f * t)
                            }
                            else -> 0f
                        }

                        if (xWeight > 0f && yWeight > 0f) {
                            val push = maxJawPush * yWeight * xWeight
                            if (dx < 0f) {
                                currX += push // 좌측 턱 -> 안쪽으로 수축
                            } else {
                                currX -= push // 우측 턱 -> 안쪽으로 수축
                            }
                        }
                    }
                }

                // ==========================================
                // 3. 얼굴 세로 길이 축소 (Face Length / Chin Lift)
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
                // 4. 몸매 슬림 (Body Slim)
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

        val needWarp = (params.faceSize > 0 || params.chinSlim > 0 || params.faceLength > 0 || params.bodySlim > 0) && landmarks != null
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
