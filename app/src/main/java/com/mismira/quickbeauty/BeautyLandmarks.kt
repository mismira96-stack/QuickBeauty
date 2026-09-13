package com.mismira.quickbeauty

import kotlin.math.max

/**
 * 감지된 얼굴 및 신체 랜드마크 정보
 */
class BeautyLandmarks(
    var imageWidth: Int = 0,
    var imageHeight: Int = 0
) {
    data class Point(var x: Float = 0f, var y: Float = 0f) {
        fun set(nx: Float, ny: Float) {
            x = nx
            y = ny
        }
    }

    data class Rect(
        var left: Float = 0f,
        var top: Float = 0f,
        var right: Float = 0f,
        var bottom: Float = 0f
    ) {
        fun set(l: Float, t: Float, r: Float, b: Float) {
            left = l
            top = t
            right = r
            bottom = b
        }

        fun width(): Float = max(0f, right - left)
        fun height(): Float = max(0f, bottom - top)
        fun centerX(): Float = (left + right) * 0.5f
        fun centerY(): Float = (top + bottom) * 0.5f
    }

    // 얼굴 정보
    var hasFace: Boolean = false
    val faceBounds = Rect()
    val faceCenter = Point()
    val chinPoint = Point()
    val leftJaw = Point()
    val rightJaw = Point()
    val leftCheek = Point()
    val rightCheek = Point()
    val mouthPoint = Point()
    val noseBase = Point()

    // 신체 정보
    var hasBody: Boolean = false
    val bodyBounds = Rect()
    val leftShoulder = Point()
    val rightShoulder = Point()
    val leftHip = Point()
    val rightHip = Point()
    var bodyCenterY: Float = 0f
    var bodyTopY: Float = 0f
    var bodyBottomY: Float = 0f

    /**
     * 감지되지 않았을 때 이미지 중앙 인물 프레이밍 기준 기본값 추정
     */
    fun setupDefaultsIfEmpty() {
        if (!hasFace) {
            val fcX = imageWidth * 0.5f
            val fcY = imageHeight * 0.35f
            val fw = imageWidth * 0.35f
            val fh = imageHeight * 0.30f
            faceBounds.set(fcX - fw * 0.5f, fcY - fh * 0.5f, fcX + fw * 0.5f, fcY + fh * 0.5f)
            faceCenter.set(fcX, fcY)
            chinPoint.set(fcX, fcY + fh * 0.45f)
            leftJaw.set(fcX - fw * 0.38f, fcY + fh * 0.25f)
            rightJaw.set(fcX + fw * 0.38f, fcY + fh * 0.25f)
            leftCheek.set(fcX - fw * 0.35f, fcY)
            rightCheek.set(fcX + fw * 0.35f, fcY)
            mouthPoint.set(fcX, fcY + fh * 0.28f)
            noseBase.set(fcX, fcY + fh * 0.08f)
            hasFace = true
        }

        if (!hasBody) {
            val bcX = imageWidth * 0.5f
            bodyTopY = imageHeight * 0.30f
            bodyBottomY = imageHeight * 0.85f
            bodyCenterY = (bodyTopY + bodyBottomY) * 0.5f
            val halfWidth = imageWidth * 0.30f
            leftShoulder.set(bcX - halfWidth, bodyTopY)
            rightShoulder.set(bcX + halfWidth, bodyTopY)
            leftHip.set(bcX - halfWidth * 0.85f, bodyCenterY)
            rightHip.set(bcX + halfWidth * 0.85f, bodyCenterY)
            bodyBounds.set(bcX - halfWidth, bodyTopY, bcX + halfWidth, bodyBottomY)
            hasBody = true
        }
    }

    /**
     * 다른 크기(예: 미리보기 비트맵 <-> 원본 고해상도 비트맵)로 스케일링 복사
     */
    fun scaleTo(targetWidth: Int, targetHeight: Int): BeautyLandmarks {
        val res = BeautyLandmarks(targetWidth, targetHeight)
        if (imageWidth <= 0 || imageHeight <= 0) return res

        val sx = targetWidth.toFloat() / imageWidth
        val sy = targetHeight.toFloat() / imageHeight

        res.hasFace = this.hasFace
        res.faceBounds.set(faceBounds.left * sx, faceBounds.top * sy, faceBounds.right * sx, faceBounds.bottom * sy)
        res.faceCenter.set(faceCenter.x * sx, faceCenter.y * sy)
        res.chinPoint.set(chinPoint.x * sx, chinPoint.y * sy)
        res.leftJaw.set(leftJaw.x * sx, leftJaw.y * sy)
        res.rightJaw.set(rightJaw.x * sx, rightJaw.y * sy)
        res.leftCheek.set(leftCheek.x * sx, leftCheek.y * sy)
        res.rightCheek.set(rightCheek.x * sx, rightCheek.y * sy)
        res.mouthPoint.set(mouthPoint.x * sx, mouthPoint.y * sy)
        res.noseBase.set(noseBase.x * sx, noseBase.y * sy)

        res.hasBody = this.hasBody
        res.bodyBounds.set(bodyBounds.left * sx, bodyBounds.top * sy, bodyBounds.right * sx, bodyBounds.bottom * sy)
        res.leftShoulder.set(leftShoulder.x * sx, leftShoulder.y * sy)
        res.rightShoulder.set(rightShoulder.x * sx, rightShoulder.y * sy)
        res.leftHip.set(leftHip.x * sx, leftHip.y * sy)
        res.rightHip.set(rightHip.x * sx, rightHip.y * sy)
        res.bodyTopY = this.bodyTopY * sy
        res.bodyBottomY = this.bodyBottomY * sy
        res.bodyCenterY = this.bodyCenterY * sy

        return res
    }
}
