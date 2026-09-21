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
    var hasReliablePose: Boolean = false
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
            if (minDim <= 0f || !hasFace) return 0f
            return max(faceBounds.width(), faceBounds.height()) / minDim
        }

    /** 40x40 미리보기 메쉬에서 얼굴이 최소 세 칸 이상 차지해야 구조 보정을 허용한다. */
    fun canAdjust(meshW: Int = 40, meshH: Int = 40): Boolean {
        if (!hasFace || imageWidth <= 0 || imageHeight <= 0) return false
        val minWidth = max(100f, imageWidth * 3f / meshW)
        val minHeight = max(100f, imageHeight * 3f / meshH)
        return faceBounds.width() >= minWidth && faceBounds.height() >= minHeight
    }

    /** 작은 얼굴이라도 실제로 일치한 포즈의 상체가 충분히 크면 체형 보정은 허용한다. */
    fun canAdjustBody(meshW: Int = 40, meshH: Int = 40): Boolean {
        if (!hasBody || imageWidth <= 0 || imageHeight <= 0) return false
        if (hasFace && canAdjust(meshW, meshH)) return true
        if (!hasReliablePose) return false
        return bodyBounds.width() >= imageWidth * 3f / meshW &&
            bodyBounds.height() >= imageHeight * 2f / meshH
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

    /** 실제 얼굴이 감지된 경우에만 누락된 신체 위치를 추정한다. */
    fun setupDefaultsIfEmpty() {
        if (!hasFace) return
        // 여러 명 사진에서 포즈가 일치하지 않으면 얼굴만으로 몸 위치를 추정하지 않는다.
        if (allFaces.size > 1 && !hasReliablePose) return

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
        res.hasReliablePose = this.hasReliablePose
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

