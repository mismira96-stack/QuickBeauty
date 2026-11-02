package com.quickbeauty.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Immutable, image-space anchors. Stored separately for each detected face. */
data class BodyJoint(val x: Float, val y: Float, val confidence: Float) {
    fun scaled(sx: Float, sy: Float) = copy(x = x * sx, y = y * sy)
}

data class PersonBody(
    val nose: BodyJoint, val leftShoulder: BodyJoint, val rightShoulder: BodyJoint,
    val leftHip: BodyJoint, val rightHip: BodyJoint
) {
    fun scaled(sx: Float, sy: Float) = PersonBody(nose.scaled(sx, sy),
        leftShoulder.scaled(sx, sy), rightShoulder.scaled(sx, sy),
        leftHip.scaled(sx, sy), rightHip.scaled(sx, sy))

    fun matches(face: BeautyLandmarks.FaceInfo, faces: List<BeautyLandmarks.FaceInfo>, w: Int, h: Int): Boolean {
        val b = face.bounds
        if (b.width() <= 0 || b.height() <= 0) return false
        val joints = listOf(nose, leftShoulder, rightShoulder, leftHip, rightHip)
        if (joints.any { !it.x.isFinite() || !it.y.isFinite() || !it.confidence.isFinite() ||
                it.confidence < .75f || it.x !in 0f..w.toFloat() || it.y !in 0f..h.toFloat() }) return false
        // A pose nose must belong to THIS face; shoulder proximity alone is insufficient.
        fun distance(f: BeautyLandmarks.FaceInfo): Float {
            val dx = (nose.x - f.noseBase.x) / max(1f, f.bounds.width())
            val dy = (nose.y - f.noseBase.y) / max(1f, f.bounds.height())
            return dx * dx + dy * dy
        }
        val own = distance(face)
        if (own > .36f || nose.x !in b.left..b.right || nose.y !in b.top..b.bottom) return false
        if (faces.any { it.index != face.index && distance(it) < own + .10f }) return false
        val shoulderY = (leftShoulder.y + rightShoulder.y) * .5f
        val hipY = (leftHip.y + rightHip.y) * .5f
        val span = abs(leftShoulder.x - rightShoulder.x)
        val hipSpan = abs(leftHip.x - rightHip.x)
        if (span !in b.width() * .8f..b.width() * 5f || hipSpan !in span * .25f..span * 1.6f) return false
        if (shoulderY - face.center.y !in b.height() * .25f..b.height() * 3f ||
            hipY - shoulderY !in b.height() * .7f..b.height() * 6f) return false
        if (abs(leftShoulder.y - rightShoulder.y) > span * .6f ||
            abs(leftHip.y - rightHip.y) > span * .6f) return false
        return face.center.x in min(leftShoulder.x, rightShoulder.x)..max(leftShoulder.x, rightShoulder.x)
    }

    fun applyTo(lm: BeautyLandmarks) {
        lm.hasBody = true
        lm.hasReliablePose = true
        lm.leftShoulder.set(leftShoulder.x, leftShoulder.y)
        lm.rightShoulder.set(rightShoulder.x, rightShoulder.y)
        lm.leftHip.set(leftHip.x, leftHip.y)
        lm.rightHip.set(rightHip.x, rightHip.y)
        lm.bodyTopY = min(leftShoulder.y, rightShoulder.y)
        lm.bodyBottomY = max(leftHip.y, rightHip.y)
        lm.bodyCenterY = (lm.bodyTopY + lm.bodyBottomY) * .5f
        val span = abs(leftShoulder.x - rightShoulder.x)
        lm.bodyBounds.set(min(leftShoulder.x, rightShoulder.x) - span * .1f, lm.bodyTopY,
            max(leftShoulder.x, rightShoulder.x) + span * .1f, lm.bodyBottomY)
    }

    /** Smooth torso protection for a different, independently verified person. */
    fun protectionAt(x: Float, y: Float): Float {
        val top = (leftShoulder.y + rightShoulder.y) * .5f
        val bottom = (leftHip.y + rightHip.y) * .5f
        val height = bottom - top
        if (height <= 0) return 0f
        val t = (y - top) / height
        if (t !in -.1f..1.1f) return 0f
        val center = (leftShoulder.x + rightShoulder.x) * .5f * (1f - t.coerceIn(0f, 1f)) +
            (leftHip.x + rightHip.x) * .5f * t.coerceIn(0f, 1f)
        val halfWidth = abs(leftShoulder.x - rightShoulder.x) * .7f
        if (halfWidth <= 0) return 0f
        fun smooth(v: Float): Float = v.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        return smooth((1f - abs(x - center) / halfWidth) / .3f) *
            smooth((t + .1f) / .2f) * smooth((1.1f - t) / .2f)
    }
}
