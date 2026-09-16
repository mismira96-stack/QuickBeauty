package com.quickbeauty.app

import kotlin.math.max
import kotlin.math.min

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
        fun contains(px: Float, py: Float): Boolean = px in left..right && py in top..bottom
    }

    /**
     * 다중 인물 식별용 얼굴 정보
     */
    data class FaceInfo(
        var index: Int = 0,
        var score: Float = 0f,
        val bounds: Rect = Rect(),
        val center: Point = Point(),
        val chinPoint: Point = Point(),
        val leftJaw: Point = Point(),
        val rightJaw: Point = Point(),
        val leftCheek: Point = Point(),
        val rightCheek: Point = Point(),
        val mouthPoint: Point = Point(),
        val noseBase: Point = Point()
    )

    // 다중 얼굴 목록 및 현재 선택된 얼굴 인덱스
    val allFaces = mutableListOf<FaceInfo>()
    var selectedFaceIndex: Int = 0

    // 현재 선택된 메인 얼굴 정보 (하위 호환)
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

    // 신체 정보 (선택된 얼굴과 물리적으로 일치하는 포즈)
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
     * 이미지 단축 대비 현재 선택된 얼굴 크기 비율 (0.0 ~ 1.0)
     * 인물이 작을 때(원거리/전신) 왜곡 방지 가둠에 활용
     */
    val faceRatio: Float
        get() {
            val minDim = min(imageWidth, imageHeight).toFloat()
            if (minDim <= 0f) return 0.25f
            return max(faceBounds.width(), faceBounds.height()) / minDim
        }

    /**
     * 특정 얼굴을 현재 활성 타깃으로 복사
     */
    fun applyFaceInfo(info: FaceInfo) {
        hasFace = true
        faceBounds.set(info.bounds.left, info.bounds.top, info.bounds.right, info.bounds.bottom)
        faceCenter.set(info.center.x, info.center.y)
        chinPoint.set(info.chinPoint.x, info.chinPoint.y)
        leftJaw.set(info.leftJaw.x, info.leftJaw.y)
        rightJaw.set(info.rightJaw.x, info.rightJaw.y)
        leftCheek.set(info.leftCheek.x, info.leftCheek.y)
        rightCheek.set(info.rightCheek.x, info.rightCheek.y)
        mouthPoint.set(info.mouthPoint.x, info.mouthPoint.y)
        noseBase.set(info.noseBase.x, info.noseBase.y)
    }

    /**
     * 감지되지 않았을 때 이미지 중앙 인물 프레이밍 기준 기본값 추정
     * (고정 비율 imageWidth * 0.25f 제거 -> 순수 인체 비례 기반 상대 스케일링)
     */
    fun setupDefaultsIfEmpty() {
        if (!hasFace) {
            val fcX = imageWidth * 0.5f
            val fcY = imageHeight * 0.35f
            val fw = imageWidth * 0.30f
            val fh = imageHeight * 0.25f
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
            val bcX = faceCenter.x
            val fw = faceBounds.width()
            val fh = faceBounds.height()
            bodyTopY = chinPoint.y + fh * 0.15f
            bodyBottomY = min(imageHeight.toFloat(), bodyTopY + fh * 2.6f)
            bodyCenterY = (bodyTopY + bodyBottomY) * 0.5f
            // 얼굴 너비의 1.3배를 어깨 반폭으로 설정하여 아기나 작은 인물에서도 어깨가 다리로 번지지 않음
            val halfWidth = fw * 1.30f
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

        res.selectedFaceIndex = this.selectedFaceIndex
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

        for (f in this.allFaces) {
            val copy = FaceInfo(
                index = f.index,
                score = f.score
            )
            copy.bounds.set(f.bounds.left * sx, f.bounds.top * sy, f.bounds.right * sx, f.bounds.bottom * sy)
            copy.center.set(f.center.x * sx, f.center.y * sy)
            copy.chinPoint.set(f.chinPoint.x * sx, f.chinPoint.y * sy)
            copy.leftJaw.set(f.leftJaw.x * sx, f.leftJaw.y * sy)
            copy.rightJaw.set(f.rightJaw.x * sx, f.rightJaw.y * sy)
            copy.leftCheek.set(f.leftCheek.x * sx, f.leftCheek.y * sy)
            copy.rightCheek.set(f.rightCheek.x * sx, f.rightCheek.y * sy)
            copy.mouthPoint.set(f.mouthPoint.x * sx, f.mouthPoint.y * sy)
            copy.noseBase.set(f.noseBase.x * sx, f.noseBase.y * sy)
            res.allFaces.add(copy)
        }

        return res
    }
}

