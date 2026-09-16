package com.quickbeauty.app

import org.junit.Assert
import org.junit.Test

class BeautyFilterEngineTest {

    @Test
    fun testParamsClampingAndReset() {
        val params = BeautyAdjustParams(-10, 150, 50, 80, 70, 0)
        Assert.assertEquals(0, params.coolTone)
        Assert.assertEquals(100, params.faceSize)
        Assert.assertEquals(50, params.chinSlim)
        Assert.assertEquals(80, params.faceLength)
        Assert.assertEquals(70, params.shoulder)
        Assert.assertEquals(0, params.bodySlim)

        Assert.assertFalse(params.isDefault())
        params.reset()
        Assert.assertTrue(params.isDefault())
        Assert.assertEquals(0, params.coolTone)
    }

    @Test
    fun testParamsCopy() {
        val original = BeautyAdjustParams(20, 30, 40, 50, 60, 70)
        val copied = original.copy()

        Assert.assertEquals(original.coolTone, copied.coolTone)
        Assert.assertEquals(original.faceSize, copied.faceSize)
        Assert.assertEquals(original.chinSlim, copied.chinSlim)
        Assert.assertEquals(original.faceLength, copied.faceLength)
        Assert.assertEquals(original.shoulder, copied.shoulder)
        Assert.assertEquals(original.bodySlim, copied.bodySlim)

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
        val zeroParams = BeautyAdjustParams(0, 0, 0, 0, 0)

        val verts = BeautyFilterEngine.computeWarpedVertices(w, h, lm, zeroParams)
        val totalVerts = 41 * 41
        Assert.assertEquals(totalVerts * 2, verts.size)

        Assert.assertEquals(0f, verts[0], 0.001f)
        Assert.assertEquals(0f, verts[1], 0.001f)
        Assert.assertEquals(1000f, verts[verts.size - 2], 0.001f)
        Assert.assertEquals(1000f, verts[verts.size - 1], 0.001f)
    }

    @Test
    fun testComputeWarpedVerticesWithFaceSizeShrinksBothXAndY() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }

        val params = BeautyAdjustParams(0, 100, 0, 0, 0)
        val warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, params)
        val original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, BeautyAdjustParams(0, 0, 0, 0, 0))

        var hasXDisplacement = false
        var hasYDisplacement = false
        for (i in warped.indices step 2) {
            val dx = kotlin.math.abs(warped[i] - original[i])
            val dy = kotlin.math.abs(warped[i + 1] - original[i + 1])
            if (dx > 1.0f) hasXDisplacement = true
            if (dy > 1.0f) hasYDisplacement = true
        }
        Assert.assertTrue("Face size reduction must shrink X dimension", hasXDisplacement)
        Assert.assertTrue("Face size reduction must shrink Y dimension (not just width)", hasYDisplacement)
    }

    @Test
    fun testComputeWarpedVerticesWithFaceLengthLiftsChinUpward() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }

        val params = BeautyAdjustParams(0, 0, 0, 100, 0)
        val warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, params)
        val original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, BeautyAdjustParams(0, 0, 0, 0, 0))

        // 턱 끝 좌표 근처의 버텍스는 위쪽으로 이동(Y값 감소)해야 함
        var chinLiftDetected = false
        val chinY = lm.chinPoint.y
        val chinX = lm.chinPoint.x

        for (i in warped.indices step 2) {
            val origX = original[i]
            val origY = original[i + 1]
            if (kotlin.math.abs(origX - chinX) < 50f && kotlin.math.abs(origY - chinY) < 50f) {
                val dy = warped[i + 1] - origY
                if (dy < -1.0f) { // Y 감소 = 위로 리프팅
                    chinLiftDetected = true
                    break
                }
            }
        }
        Assert.assertTrue("Face length adjustment should lift chin upward", chinLiftDetected)
    }

    @Test
    fun testComputeWarpedVerticesWithShoulderBroadeningExpandsShoulders() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }

        val params = BeautyAdjustParams(0, 0, 0, 0, 100, 0)
        val warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, params)
        val original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, BeautyAdjustParams(0, 0, 0, 0, 0, 0))

        var leftShoulderPushedLeft = false
        var rightShoulderPushedRight = false

        val leftSX = lm.leftShoulder.x
        val rightSX = lm.rightShoulder.x
        val sY = lm.leftShoulder.y

        for (i in warped.indices step 2) {
            val origX = original[i]
            val origY = original[i + 1]

            // 좌측 어깨 부근: X가 왼쪽(감소)으로 밀려야 함
            if (kotlin.math.abs(origX - leftSX) < 60f && kotlin.math.abs(origY - sY) < 60f) {
                if (warped[i] < origX - 1.0f) {
                    leftShoulderPushedLeft = true
                }
            }
            // 우측 어깨 부근: X가 오른쪽(증가)으로 밀려야 함
            if (kotlin.math.abs(origX - rightSX) < 60f && kotlin.math.abs(origY - sY) < 60f) {
                if (warped[i] > origX + 1.0f) {
                    rightShoulderPushedRight = true
                }
            }
        }

        Assert.assertTrue("Shoulder broadening must push left shoulder outward (left)", leftShoulderPushedLeft)
        Assert.assertTrue("Shoulder broadening must push right shoulder outward (right)", rightShoulderPushedRight)
    }

    @Test
    fun testMonotonicityNoMeshInversion() {
        val w = 1000
        val h = 1000
        val lm = BeautyLandmarks(w, h).apply { setupDefaultsIfEmpty() }
        val maxParams = BeautyAdjustParams(100, 100, 100, 100, 100, 100)

        val verts = BeautyFilterEngine.computeWarpedVertices(w, h, lm, maxParams)
        val meshW = BeautyFilterEngine.MESH_W
        val meshH = BeautyFilterEngine.MESH_H

        // 모든 가로 행에서 X 좌표가 엄격하게 증가해야 함 (메쉬 뒤집힘 없음)
        for (r in 0..meshH) {
            for (c in 0 until meshW) {
                val idx1 = (r * (meshW + 1) + c) * 2
                val idx2 = (r * (meshW + 1) + (c + 1)) * 2
                val x1 = verts[idx1]
                val x2 = verts[idx2]
                Assert.assertTrue("Row $r, col $c: x2 ($x2) must be strictly greater than x1 ($x1)", x2 > x1)
            }
        }

        // 모든 세로 열에서 Y 좌표가 엄격하게 증가해야 함 (메쉬 뒤집힘 없음)
        for (c in 0..meshW) {
            for (r in 0 until meshH) {
                val idx1 = (r * (meshW + 1) + c) * 2 + 1
                val idx2 = ((r + 1) * (meshW + 1) + c) * 2 + 1
                val y1 = verts[idx1]
                val y2 = verts[idx2]
                Assert.assertTrue("Col $c, row $r: y2 ($y2) must be strictly greater than y1 ($y1)", y2 > y1)
            }
        }
    }

    @Test
    fun testSmallPersonStrictClampingPreventsLegDistortion() {
        val w = 1000
        val h = 2000
        val lm = BeautyLandmarks(w, h)
        // 아기/원거리 전신 사진 시뮬레이션: 얼굴 높이 100px (전체 2000px의 5%)
        val fcX = 500f
        val fcY = 700f
        val fw = 80f
        val fh = 100f
        lm.faceBounds.set(fcX - fw * 0.5f, fcY - fh * 0.5f, fcX + fw * 0.5f, fcY + fh * 0.5f)
        lm.faceCenter.set(fcX, fcY)
        lm.chinPoint.set(fcX, fcY + fh * 0.5f)
        lm.hasFace = true
        lm.setupDefaultsIfEmpty()

        val shoulderParams = BeautyAdjustParams(0, 0, 0, 0, 100, 0)
        val warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, shoulderParams)
        val original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, BeautyAdjustParams())

        // 다리 및 바닥 영역: Y >= 1300f (상체 아래 무릎, 다리, 바닥 영역)
        // 어깨 보정이 100이어도 다리/바닥 버텍스의 변위(dx, dy)는 0이어야 함!
        var legMaxDisplacement = 0f
        for (i in warped.indices step 2) {
            val origX = original[i]
            val origY = original[i + 1]
            if (origY >= 1300f) {
                val dx = kotlin.math.abs(warped[i] - origX)
                val dy = kotlin.math.abs(warped[i + 1] - origY)
                val disp = kotlin.math.max(dx, dy)
                if (disp > legMaxDisplacement) {
                    legMaxDisplacement = disp
                }
            }
        }

        Assert.assertEquals("Shoulder broaden must NOT distort leg/floor area (displacement must be 0)", 0f, legMaxDisplacement, 0.001f)
    }

    @Test
    fun testMultiFaceSetupAndFaceRatio() {
        val lm = BeautyLandmarks(1000, 1000)
        lm.faceBounds.set(450f, 300f, 550f, 400f) // 100x100 얼굴 (10%)
        lm.hasFace = true

        Assert.assertEquals(0.10f, lm.faceRatio, 0.01f)

        // 다중 얼굴 추가
        val f1 = BeautyLandmarks.FaceInfo(0, 0.85f)
        f1.bounds.set(450f, 300f, 550f, 400f)
        val f2 = BeautyLandmarks.FaceInfo(1, 0.40f)
        f2.bounds.set(100f, 200f, 150f, 250f)
        lm.allFaces.add(f1)
        lm.allFaces.add(f2)

        Assert.assertEquals(2, lm.allFaces.size)
        Assert.assertEquals(0, lm.selectedFaceIndex)
    }
}

