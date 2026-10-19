package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class FaceSelectionTest {
    private fun face(i: Int, l: Float, t: Float, r: Float, b: Float) =
        BeautyLandmarks.FaceInfo(index = i).apply { bounds.set(l, t, r, b) }

    @Test fun nestedDuplicateDoesNotBecomeAnotherSelectableFace() {
        val large = face(0, 100f, 100f, 500f, 500f)
        val duplicate = face(1, 210f, 200f, 370f, 370f)
        val distantSmall = face(2, 600f, 100f, 720f, 250f)
        val kept = FaceSelection.removeDuplicates(listOf(duplicate, distantSmall, large))
        assertEquals(listOf(0, 2), kept.map { it.index })
    }

    @Test fun smallRealThirdPersonSurvivesRelativeAreaDifference() {
        val kept = FaceSelection.removeDuplicates(listOf(face(0, 0f, 0f, 400f, 400f),
            face(1, 500f, 100f, 650f, 250f), face(2, 800f, 100f, 900f, 210f)))
        assertEquals(3, kept.size)
    }

    @Test fun directFaceHitBeatsNearbyLargeExpandedMargin() {
        val targets = listOf(FaceSelection.Target(0, BeautyLandmarks.Rect(0f, 0f, 400f, 400f)),
            FaceSelection.Target(1, BeautyLandmarks.Rect(420f, 100f, 520f, 240f)))
        repeat(5) { assertEquals(1, FaceSelection.find(425f, 170f, targets, 0, 64f)) }
        assertEquals(null, FaceSelection.find(800f, 600f, targets, 0, 64f))
    }

    @Test fun closelyAdjacentPeopleRemainSeparate() {
        assertEquals(2, FaceSelection.removeDuplicates(listOf(face(0, 100f, 100f, 300f, 350f),
            face(1, 250f, 100f, 450f, 350f))).size)
    }
}
