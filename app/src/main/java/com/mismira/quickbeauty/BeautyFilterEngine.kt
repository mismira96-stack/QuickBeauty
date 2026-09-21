package com.quickbeauty.app

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
        val shoulderFactor = (params.shoulder / 100.0f).coerceIn(0f, 1f)
        val bodySlimFactor = (params.bodySlim / 100.0f).coerceIn(0f, 1f)

        val hasFace = landmarks?.canAdjust(meshW, meshH) == true
        val hasBody = hasFace && landmarks?.hasBody == true

        val fc = landmarks?.faceCenter
        val fcX = fc?.x ?: (width * 0.5f)
        val fcY = fc?.y ?: (height * 0.35f)

        val fw = max(20f, landmarks?.faceBounds?.width() ?: (width * 0.35f))
        val fh = max(20f, landmarks?.faceBounds?.height() ?: (height * 0.35f))

        // 인물 크기 비율 기반 왜곡 방지 감쇠 계수 (인물이 작을수록 배경 뒤틀림 방지를 위해 미세 보정 강도 조절)
        val faceRatio = landmarks?.faceRatio ?: (max(fw, fh) / min(width, height))
        val scaleDamping = if (faceRatio < 0.08f) {
            (faceRatio / 0.08f).coerceIn(0.20f, 1.0f)
        } else {
            1.0f
        }

        // 1. 얼굴 전체 크기 축소 (소두 효과 - X/Y 비율 유지 전체 축소) 파라미터
        val headRadius = max(fw, fh) * 0.70f
        val headInnerR = headRadius * 0.60f
        val headOuterR = headRadius * 1.35f
        val maxHeadScale = 0.085f * faceSizeFactor * scaleDamping

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
        val maxJawPush = fw * 0.060f * chinSlimFactor * scaleDamping

        // 3. 얼굴 세로 길이 축소 (하관 리프팅 / 긴 얼굴 단축) 파라미터
        // 아기나 웃는 표정에서도 랜드마크 Y 순서(코 < 입 < 턱)가 뒤집히지 않도록 안전 클램핑
        val safeNoseY = min(noseBaseY, fcY + fh * 0.15f)
        val safeChinY = max(chinY, safeNoseY + fh * 0.30f)
        val safeMouthY = mouthY.coerceIn(safeNoseY + fh * 0.08f, safeChinY - fh * 0.06f)

        val chinToMouthSpan = max(10f, safeChinY - safeMouthY)
        val mouthToNoseSpan = max(10f, safeMouthY - safeNoseY)
        // 턱 밑 감쇠 폭: 아기나 목이 짧은 체형에서 티셔츠/옷깃이 위로 빨려 올라가지 않도록 초근접 영역으로 제한
        val neckSpan = min(fh * 0.12f, 25f).coerceAtLeast(8f)
        val safeNeckY = safeChinY + neckSpan

        val maxChinLift = fh * 0.065f * faceLengthFactor * scaleDamping
        val lengthInnerW = fw * 0.30f
        val lengthOuterW = fw * 0.85f

        // 4. 어깨 넓히기 (직각 어깨) 파라미터 - 상체 보정력 보존 & 다리 침범 차단
        val shoulderCenterY: Float
        val leftShoulderX: Float
        val rightShoulderX: Float
        if (hasBody && landmarks.leftShoulder.x > 0) {
            shoulderCenterY = (landmarks.leftShoulder.y + landmarks.rightShoulder.y) * 0.5f
            leftShoulderX = min(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
            rightShoulderX = max(landmarks.leftShoulder.x, landmarks.rightShoulder.x)
        } else {
            // 얼굴 위치 기준 인체 비율 추정 (상반신 포트레이트)
            shoulderCenterY = chinY + fh * 0.40f
            leftShoulderX = fcX - fw * 1.30f
            rightShoulderX = fcX + fw * 1.30f
        }
        val shoulderCenterX = (leftShoulderX + rightShoulderX) * 0.5f
        val rawSpanX = max(30f, rightShoulderX - leftShoulderX)
        // 얼굴은 작지만 상체가 큰 체형을 위해 어깨 너비 상한선을 fw * 3.0f로 여유 있게 허용
        val shoulderSpanX = min(rawSpanX, fw * 3.0f)
        val shoulderHalfSpan = shoulderSpanX * 0.5f

        // 얼굴이 작아도 몸이 크면 보정이 지나치게 죽지 않도록 effectiveRatio 재계산
        val bodyRatio = (shoulderSpanX / min(width, height)).coerceIn(0f, 1f)
        val effectiveScaleDamping = if (max(faceRatio, bodyRatio * 0.55f) < 0.08f) {
            (max(faceRatio, bodyRatio * 0.55f) / 0.08f).coerceIn(0.25f, 1.0f)
        } else {
            1.0f
        }

        val maxShoulderPush = shoulderSpanX * 0.055f * shoulderFactor * effectiveScaleDamping

        val shoulderTopY = chinY + fh * 0.08f
        // 어깨 하단: 상체와 가슴까지 자연스럽게 연결하되, 엉덩이/골반(leftHip) 이전에서 안전 종료
        val hipY = if (hasBody && landmarks.leftHip.y > shoulderCenterY) landmarks.leftHip.y else (shoulderCenterY + fh * 2.2f)
        val maxShoulderBot = min(hipY - 10f, shoulderCenterY + max(fh * 1.15f, shoulderHalfSpan * 0.65f))
        val shoulderBotY = min(height.toFloat(), maxShoulderBot)
        val shoulderYSpan = max(20f, shoulderBotY - shoulderTopY)

        // 5. 몸매 슬림 파라미터 - 어깨와 분리된 허리/복부 집중 영역 (무릎/다리/바닥 번짐 완전 차단)
        val bodyTopY = shoulderCenterY + fh * 0.35f
        val rawBottomY = if (hasBody && landmarks.leftHip.y > bodyTopY) {
            // 골반 위치가 감지된 경우 골반 약간 아래까지만 허용 (무릎/다리 침범 방지)
            landmarks.leftHip.y + fh * 0.45f
        } else {
            bodyTopY + fh * 2.5f
        }
        val bodyBottomY = min(min(height.toFloat(), bodyTopY + fh * 3.0f), rawBottomY)
        val bodyCenterY = (bodyTopY + bodyBottomY) * 0.5f
        val bodyCenterX = landmarks?.bodyBounds?.centerX() ?: fcX
        val bodyHalfHeight = max(20f, (bodyBottomY - bodyTopY) * 0.5f)
        val rawHalfWidth = (landmarks?.bodyBounds?.width() ?: (fw * 1.6f)) * 0.55f
        val bodyHalfWidth = min(rawHalfWidth, fw * 2.2f)

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
                // ==========================================
                // 3. 얼굴 세로 길이 축소 (Face Length / 하관 동안 단축)
                // 코 밑 ~ 턱 끝을 위로 부드럽게 리프팅하여 긴 하관/중안부 개선
                // 머리 위 배경 왜곡 방지를 위해 이마 하향 변위는 완전 제거
                // 목 및 티셔츠/옷깃 침범 방지를 위해 턱 밑 감쇠를 초근접 영역(safeNeckY)으로 엄격 제한
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

                        when {
                            // A. 턱 끝 ~ 턱 바로 밑: 턱 끝(1.0)에서 초근접 턱밑(0.0)으로 급격 감쇠 -> 옷/가슴 침범 0%
                            origY >= safeChinY && origY <= safeNeckY -> {
                                val t = (safeNeckY - origY) / neckSpan
                                liftRatio = t * t * (3f - 2f * t)
                            }
                            // B. 입술 ~ 턱 끝: 턱 끝(1.0)에서 입술(0.35)로 감쇠하며 턱 길이 단축
                            origY in safeMouthY..safeChinY -> {
                                val t = (origY - safeMouthY) / chinToMouthSpan
                                liftRatio = 0.35f + 0.65f * (t * t * (3f - 2f * t))
                            }
                            // C. 코 밑 ~ 입술: 입술(0.35)에서 코 밑(0.0)으로 감쇠하며 인중 살짝 단축
                            origY in safeNoseY..safeMouthY -> {
                                val t = (origY - safeNoseY) / mouthToNoseSpan
                                liftRatio = 0.35f * (t * t * (3f - 2f * t))
                            }
                        }

                        if (liftRatio > 0f) {
                            currY -= maxChinLift * liftRatio * xWeight
                        }
                    }
                }

                // ==========================================
                // 4. 어깨 넓히기 (Shoulder Broaden / 직각 어깨)
                // 목은 안전하게 보호하고, 어깨선만 바깥으로 확장하여 당당한 직각 어깨 핏 형성
                // ==========================================
                if (shoulderFactor > 0.001f && hasBody && origY in shoulderTopY..shoulderBotY) {
                    val yNorm = (origY - shoulderTopY) / shoulderYSpan
                    val yWeight = sin(yNorm * Math.PI.toFloat())

                    val dx = origX - shoulderCenterX
                    val absDx = abs(dx)

                    // 목 보호 영역 (안쪽 25% 고정), 피크는 90%, 외곽은 145%까지 자연스러운 감쇄
                    val neckProtectW = shoulderHalfSpan * 0.25f
                    val shoulderPeakW = shoulderHalfSpan * 0.90f
                    val shoulderOuterW = shoulderHalfSpan * 1.45f

                    val xWeight: Float = when {
                        absDx <= neckProtectW -> 0f
                        absDx < shoulderPeakW -> {
                            val t = (absDx - neckProtectW) / (shoulderPeakW - neckProtectW)
                            t * t * (3f - 2f * t)
                        }
                        absDx <= shoulderOuterW -> {
                            val t = (shoulderOuterW - absDx) / (shoulderOuterW - shoulderPeakW)
                            t * t * (3f - 2f * t)
                        }
                        else -> 0f
                    }

                    if (xWeight > 0f && yWeight > 0f) {
                        // 사진 경계에 닿은 옷/배경은 이동시키지 않고 안쪽에서 서서히 감쇠한다.
                        val edgeSpan = max(width * 0.08f, shoulderHalfSpan * 0.5f)
                        val edgeT = (min(origX, width - origX) / edgeSpan).coerceIn(0f, 1f)
                        val edgeWeight = edgeT * edgeT * (3f - 2f * edgeT)
                        val push = maxShoulderPush * yWeight * xWeight * edgeWeight
                        if (dx < 0f) {
                            currX -= push // 좌측 어깨 -> 바깥쪽(왼쪽)으로 확장
                        } else {
                            currX += push // 우측 어깨 -> 바깥쪽(오른쪽)으로 확장
                        }
                        // 직각 어깨 미세 리프팅 (외곽 어깨 끝을 살짝 올려 처진 어깨 반듯하게 교정)
                        if (absDx >= shoulderHalfSpan * 0.50f) {
                            val liftWeight = ((absDx - shoulderHalfSpan * 0.50f) / (shoulderHalfSpan * 0.50f)).coerceIn(0f, 1f)
                            currY -= push * 0.18f * liftWeight
                        }
                    }
                }

                // ==========================================
                // 5. 몸매 슬림 (Body Slim)
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
                                val edgeSpan = max(width * 0.08f, bodyHalfWidth * 0.5f)
                                val edgeT = (min(origX, width - origX) / edgeSpan).coerceIn(0f, 1f)
                                val edgeWeight = edgeT * edgeT * (3f - 2f * edgeT)
                                val totalWeight = yWeight * xWeight * bodySlimFactor * 0.22f * edgeWeight
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

        val needWarp = (params.faceSize > 0 || params.chinSlim > 0 || params.faceLength > 0 || params.shoulder > 0 || params.bodySlim > 0) && landmarks?.canAdjust() == true
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

