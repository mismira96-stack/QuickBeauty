package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class PersonBodyTest {
    private fun face(index: Int, dx: Float = 0f) = BeautyLandmarks.FaceInfo(index).apply {
        bounds.set(150f + dx, 100f, 250f + dx, 220f)
        center.set(200f + dx, 160f); noseBase.set(200f + dx, 170f)
        chinPoint.set(200f + dx, 220f)
    }
    private fun body(dx: Float = 0f) = PersonBody(BodyJoint(200f + dx, 165f, .99f),
        BodyJoint(100f + dx, 280f, .99f), BodyJoint(300f + dx, 280f, .99f),
        BodyJoint(130f + dx, 570f, .99f), BodyJoint(270f + dx, 570f, .99f))

    @Test fun poseMustOwnTheSelectedFaceNotJustNearbyShoulders() {
        val a = face(0); val b = face(1, 300f)
        assertTrue(body().matches(a, listOf(a, b), 1000, 1000))
        assertFalse(body().matches(b, listOf(a, b), 1000, 1000))
        assertFalse(body().copy(leftHip = BodyJoint(130f, 570f, .1f)).matches(a, listOf(a, b), 1000, 1000))
        assertFalse(body().matches(a, listOf(a, face(1, 2f)), 1000, 1000))
    }

    @Test fun matchedBodiesAndIndividualEditsSurviveSelectionScalingAndExport() {
        val lm = BeautyLandmarks(1000, 1000)
        lm.allFaces.addAll(listOf(face(0), face(1, 500f)))
        lm.bodiesByFaceIndex[0] = body()
        lm.bodiesByFaceIndex[1] = body(500f)
        lm.applyFaceInfo(lm.allFaces[0]); body().applyTo(lm)
        assertTrue(lm.canAdjustBody())
        val edits = mapOf(0 to BeautyAdjustParams(bodySlim = 100))
        val a = BeautyFilterEngine.computeWarpedVertices(1000, 1000, lm, BeautyAdjustParams(bodySlim = 100), faceParamsByIndex = edits)
        lm.selectedFaceIndex = 1; lm.applyFaceInfo(lm.allFaces[1]); body(500f).applyTo(lm)
        val b = BeautyFilterEngine.computeWarpedVertices(1000, 1000, lm, BeautyAdjustParams(), faceParamsByIndex = edits)
        assertArrayEquals(a, b, .001f)
        val base = BeautyFilterEngine.computeWarpedVertices(1000, 1000, lm, BeautyAdjustParams())
        assertTrue(a.indices.any { kotlin.math.abs(a[it] - base[it]) > .1f })
        // Neighbor's face and distant body stay unchanged.
        for (r in 0..60) for (c in 35..60) {
            val i = (r * 61 + c) * 2
            assertEquals(base[i], a[i], .001f)
            assertEquals(base[i + 1], a[i + 1], .001f)
        }
        val scaled = lm.scaleTo(2000, 2000)
        assertEquals(400f, scaled.bodiesByFaceIndex[0]!!.nose.x, .001f)
        assertEquals(700f, lm.bodiesByFaceIndex[1]!!.nose.x, .001f)
        for (r in 0..60) for (c in 0 until 60) {
            val i = (r * 61 + c) * 2
            assertTrue("Mesh folded", a[i + 2] > a[i])
        }
    }
}
