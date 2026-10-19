package com.quickbeauty.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Geometry-only selection rules, shared by the detector and preview tests. */
internal object FaceSelection {
    data class Target(val index: Int, val bounds: BeautyLandmarks.Rect)

    fun removeDuplicates(faces: List<BeautyLandmarks.FaceInfo>): List<BeautyLandmarks.FaceInfo> {
        val kept = mutableListOf<BeautyLandmarks.FaceInfo>()
        // Larger duplicate boxes are retained; distant small faces are unaffected.
        for (face in faces.sortedByDescending { it.bounds.width() * it.bounds.height() }) {
            val a = face.bounds
            if (kept.none { other ->
                val b = other.bounds
                val overlap = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
                    max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
                val area = min(a.width() * a.height(), b.width() * b.height())
                area > 0f && overlap / area >= .80f &&
                    abs(a.centerX() - b.centerX()) <= b.width() * .25f &&
                    abs(a.centerY() - b.centerY()) <= b.height() * .25f
            }) kept.add(face)
        }
        return kept
    }

    fun find(x: Float, y: Float, targets: List<Target>, selected: Int, minRadius: Float): Int? {
        data class Candidate(val index: Int, val direct: Boolean, val score: Float)
        val candidates = targets.mapNotNull { t ->
            val b = t.bounds
            if (b.width() <= 0f || b.height() <= 0f) return@mapNotNull null
            val dx = x - b.centerX(); val dy = y - b.centerY()
            val direct = x in b.left..b.right && y in b.top..b.bottom
            val ex = max(b.width() * .20f, minRadius - b.width() * .5f)
            val ey = max(b.height() * .20f, minRadius - b.height() * .5f)
            if (x !in (b.left - ex)..(b.right + ex) || y !in (b.top - ey)..(b.bottom + ey)) null
            else Candidate(t.index, direct, dx * dx / (b.width() * b.width()) + dy * dy / (b.height() * b.height()))
        }
        val direct = candidates.filter { it.direct }
        val pool = direct.ifEmpty { candidates }
        val best = pool.minByOrNull { it.score } ?: return null
        // Keep the current selection only in genuinely ambiguous expanded margins.
        if (direct.isEmpty()) pool.firstOrNull { it.index == selected && it.score <= best.score + .15f }?.let { return it.index }
        return best.index
    }
}
