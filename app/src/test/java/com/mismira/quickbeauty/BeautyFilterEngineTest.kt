package com.mismira.quickbeauty

import org.junit.Assert
import org.junit.Test

class BeautyFilterEngineTest {

    @Test
    fun testParamsClampingAndReset() {
        val params = BeautyAdjustParams(-10, 150, 50, 0)
        Assert.assertEquals(0, params.coolTone)
        Assert.assertEquals(100, params.faceSlim)
        Assert.assertEquals(50, params.chinSlim)
        Assert.assertEquals(0, params.bodySlim)

        Assert.assertFalse(params.isDefault())
        params.reset()
        Assert.assertTrue(params.isDefault())
        Assert.assertEquals(0, params.coolTone)
    }

    @Test
    fun testParamsCopy() {
        val original = BeautyAdjustParams(20, 30, 40, 50)
        val copied = original.copy()

        Assert.assertEquals(original.coolTone, copied.coolTone)
        Assert.assertEquals(original.faceSlim, copied.faceSlim)

        copied.coolTone = 90
        Assert.assertEquals(20, original.coolTone)
        Assert.assertEquals(90, copied.coolTone)
    }

    @Test
    fun testCoolToneMatrixCalculations() {
        val arr0 = BeautyFilterEngine.getCoolToneMatrixArray(0)
        Assert.assertEquals(1.0f, arr0[0], 0.001f) // R
        Assert.assertEquals(1.0f, arr0[6], 0.001f) // G
        Assert.assertEquals(1.0f, arr0[12], 0.001f) // B
        Assert.assertEquals(0.0f, arr0[4], 0.001f) // Offset

        val arr100 = BeautyFilterEngine.getCoolToneMatrixArray(100)
        Assert.assertTrue("B scale should be boosted for cool tone", arr100[12] > 1.1f)
        Assert.assertTrue("Brightness offset should be added", arr100[4] > 10.0f)
        Assert.assertTrue("G scale should be slightly attenuated to remove yellow", arr100[6] < 1.0f)
    }

    @Test
    fun testLandmarksDefaultsAndScale() {
        val lm = BeautyLandmarks(1000, 2000)
        lm.setupDefaultsIfEmpty()

        Assert.assertTrue(lm.hasFace)
        Assert.assertTrue(lm.hasBody)
        Assert.assertEquals(500f, lm.faceCenter.x, 0.1f)
        Assert.assertEquals(700f, lm.faceCenter.y, 0.1f)

        val scaled = lm.scaleTo(500, 1000)
        Assert.assertEquals(500, scaled.imageWidth)
        Assert.assertEquals(1000, scaled.imageHeight)
        Assert.assertEquals(250f, scaled.faceCenter.x, 0.1f)
        Assert.assertEquals(350f, scaled.faceCenter.y, 0.1f)
    }

    @Test
    fun testComputeWarpedVerticesZeroParamsMatchesOriginalGrid() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }
        val zeroParams = BeautyAdjustParams(0, 0, 0, 0)

        val verts = BeautyFilterEngine.computeWarpedVertices(w, h, lm, zeroParams)
        val totalVerts = 41 * 41
        Assert.assertEquals(totalVerts * 2, verts.size)

        Assert.assertEquals(0f, verts[0], 0.001f)
        Assert.assertEquals(0f, verts[1], 0.001f)
        Assert.assertEquals(1000f, verts[verts.size - 2], 0.001f)
        Assert.assertEquals(1000f, verts[verts.size - 1], 0.001f)
    }

    @Test
    fun testComputeWarpedVerticesWithFaceSlimShiftsInward() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }

        val params = BeautyAdjustParams(0, 100, 0, 0)
        val warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, params)
        val original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, BeautyAdjustParams(0, 0, 0, 0))

        var hasDisplacement = false
        for (i in warped.indices step 2) {
            val dx = kotlin.math.abs(warped[i] - original[i])
            val dy = kotlin.math.abs(warped[i + 1] - original[i + 1])
            if (dx > 0.5f || dy > 0.5f) {
                hasDisplacement = true
                break
            }
        }
        Assert.assertTrue("Face slim should produce vertex displacement", hasDisplacement)
    }
}
