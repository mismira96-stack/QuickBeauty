package com.mismira.quickbeauty;

import org.junit.Assert;
import org.junit.Test;

public class BeautyFilterEngineTest {

    @Test
    public void testParamsClampingAndReset() {
        BeautyAdjustParams params = new BeautyAdjustParams(-10, 150, 50, 0);
        Assert.assertEquals(0, params.getCoolTone());
        Assert.assertEquals(100, params.getFaceSlim());
        Assert.assertEquals(50, params.getChinSlim());
        Assert.assertEquals(0, params.getBodySlim());

        Assert.assertFalse(params.isDefault());
        params.reset();
        Assert.assertTrue(params.isDefault());
        Assert.assertEquals(0, params.getCoolTone());
    }

    @Test
    public void testParamsCopy() {
        BeautyAdjustParams original = new BeautyAdjustParams(20, 30, 40, 50);
        BeautyAdjustParams copied = original.copy();

        Assert.assertEquals(original.getCoolTone(), copied.getCoolTone());
        Assert.assertEquals(original.getFaceSlim(), copied.getFaceSlim());

        copied.setCoolTone(90);
        Assert.assertEquals(20, original.getCoolTone());
        Assert.assertEquals(90, copied.getCoolTone());
    }

    @Test
    public void testCoolToneMatrixCalculations() {
        float[] arr0 = BeautyFilterEngine.getCoolToneMatrixArray(0);
        Assert.assertEquals(1.0f, arr0[0], 0.001f); // R
        Assert.assertEquals(1.0f, arr0[6], 0.001f); // G
        Assert.assertEquals(1.0f, arr0[12], 0.001f); // B
        Assert.assertEquals(0.0f, arr0[4], 0.001f); // Offset

        float[] arr100 = BeautyFilterEngine.getCoolToneMatrixArray(100);
        // B 채널은 1.0보다 커야 하고(쿨톤 부스트), 오프셋(명도)도 증가해야 함
        Assert.assertTrue("B scale should be boosted for cool tone", arr100[12] > 1.1f);
        Assert.assertTrue("Brightness offset should be added", arr100[4] > 10.0f);
        Assert.assertTrue("G scale should be slightly attenuated to remove yellow", arr100[6] < 1.0f);
    }

    @Test
    public void testLandmarksDefaultsAndScale() {
        BeautyLandmarks lm = new BeautyLandmarks(1000, 2000);
        lm.setupDefaultsIfEmpty();

        Assert.assertTrue(lm.hasFace);
        Assert.assertTrue(lm.hasBody);
        Assert.assertEquals(500f, lm.faceCenter.x, 0.1f);
        Assert.assertEquals(700f, lm.faceCenter.y, 0.1f);

        // ?ㅼ??쇰쭅 ?뚯뒪??(媛濡쒖꽭濡??덈컲)
        BeautyLandmarks scaled = lm.scaleTo(500, 1000);
        Assert.assertEquals(500, scaled.imageWidth);
        Assert.assertEquals(1000, scaled.imageHeight);
        Assert.assertEquals(250f, scaled.faceCenter.x, 0.1f);
        Assert.assertEquals(350f, scaled.faceCenter.y, 0.1f);
    }

    @Test
    public void testComputeWarpedVerticesZeroParamsMatchesOriginalGrid() {
        int w = 1000;
        int h = 1000;
        BeautyLandmarks lm = new BeautyLandmarks(w, h);
        lm.setupDefaultsIfEmpty();
        BeautyAdjustParams zeroParams = new BeautyAdjustParams(0, 0, 0, 0);

        float[] verts = BeautyFilterEngine.computeWarpedVertices(w, h, lm, zeroParams);
        int totalVerts = 41 * 41;
        Assert.assertEquals(totalVerts * 2, verts.length);

        // 泥?踰덉㎏ 踰꾪뀓?ㅻ뒗 (0,0), 留덉?留?踰꾪뀓?ㅻ뒗 (1000, 1000)
        Assert.assertEquals(0f, verts[0], 0.001f);
        Assert.assertEquals(0f, verts[1], 0.001f);
        Assert.assertEquals(1000f, verts[verts.length - 2], 0.001f);
        Assert.assertEquals(1000f, verts[verts.length - 1], 0.001f);
    }

    @Test
    public void testComputeWarpedVerticesWithFaceSlimShiftsInward() {
        int w = 1000;
        int h = 1000;
        BeautyLandmarks lm = new BeautyLandmarks(w, h);
        lm.setupDefaultsIfEmpty();

        BeautyAdjustParams params = new BeautyAdjustParams(0, 100, 0, 0);
        float[] warped = BeautyFilterEngine.computeWarpedVertices(w, h, lm, params);
        float[] original = BeautyFilterEngine.computeWarpedVertices(w, h, lm, new BeautyAdjustParams(0, 0, 0, 0));

        // ?쇨뎬 以묒떖 二쇰? 踰꾪뀓?ㅻ뱾???먮낯怨??щ씪議뚮뒗吏 ?뺤씤
        boolean hasDisplacement = false;
        for (int i = 0; i < warped.length; i += 2) {
            float dx = Math.abs(warped[i] - original[i]);
            float dy = Math.abs(warped[i + 1] - original[i + 1]);
            if (dx > 0.5f || dy > 0.5f) {
                hasDisplacement = true;
                break;
            }
        }
        Assert.assertTrue("Face slim should produce vertex displacement", hasDisplacement);
    }
}