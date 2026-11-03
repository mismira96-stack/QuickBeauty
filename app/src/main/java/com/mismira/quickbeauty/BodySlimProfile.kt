package com.quickbeauty.app

import kotlin.math.abs

/** Local transfer plateau protects arm-width bands; outer background still needs falloff. */
internal object BodySlimProfile {
    const val INNER = .30f
    const val ARM_END = .85f
    const val OUTER = 1.50f

    fun inwardDistance(dx: Float, radius: Float): Float {
        if (!dx.isFinite() || !radius.isFinite() || radius <= 0f) return 0f
        val u = abs(dx) / radius
        val peak = radius * (4f / 27f) // same peak magnitude as u*(1-u)^2
        val weight = when {
            u < INNER -> (u / INNER).let { 2f * it - it * it }
            u <= ARM_END -> 1f
            u < OUTER -> ((u - ARM_END) / (OUTER - ARM_END)).let { 1f - it * it * (3f - 2f * it) }
            else -> 0f
        }
        return (if (dx < 0f) -1f else 1f) * peak * weight
    }
}
