package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class StoryLayoutTest {
    @org.junit.Test fun photoCanMoveLeftAtExactFrameAspect() {
        val t=StoryTemplateType.CLEAN_PURE
        val p=StoryPhotoPosition(.5f,.5f)
        val before=StoryLayout.photo(1048,1872,t,p)
        val moved=StoryLayout.pan(1048,1872,t,p,-260f,0f)
        val after=StoryLayout.photo(1048,1872,t,moved)
        org.junit.Assert.assertEquals(before.left-260f,after.left,.001f)
        org.junit.Assert.assertTrue(after.right<StoryLayout.frame(t).right)
        val extreme=StoryLayout.photo(1048,1872,t,StoryLayout.pan(1048,1872,t,moved,-100000f,0f))
        org.junit.Assert.assertTrue(extreme.right>=StoryLayout.frame(t).left+63.99f)
    }
    @org.junit.Test fun textCanReachFormerInstagramFooter() {
        val bottom=StoryLayout.block(300f,80f,StoryTextPosition(.5f,1f))
        org.junit.Assert.assertEquals(1920f,bottom.bottom,.001f)
        val top=StoryLayout.block(300f,80f,StoryTextPosition(.5f,0f))
        org.junit.Assert.assertEquals(0f,top.top,.001f)
    }
    @Test fun allTextRegionsStayInsideInstagramSafeZone() {
        for(t in StoryTemplateType.entries) {
            val b=StoryLayout.textRegion(t)
            assertTrue(b.top>=0 && b.bottom<=1920)
            assertTrue(b.left>=24 && b.right<=1056)
        }
    }
    @Test fun fitPreservesPhotoAndCropModesFillTheirFrame() {
        for((w,h) in listOf(4000 to 3000,3000 to 4000,1080 to 1920,1000 to 8000,8000 to 1000,1000 to 1000))
            for(t in StoryTemplateType.entries) {
                val photo=StoryLayout.photo(w,h,t);val frame=StoryLayout.frame(t)
                assertEquals(w.toDouble()/h,(photo.width/photo.height).toDouble(),.0001)
                if(t!=StoryTemplateType.MUTED_TONE) {
                    assertTrue(photo.left<=frame.left+.01 && photo.top<=frame.top+.01)
                    assertTrue(photo.right>=frame.right-.01 && photo.bottom>=frame.bottom-.01)
                } else {
                    assertTrue(photo.left>=frame.left-.01 && photo.right<=frame.right+.01)
                    assertTrue(photo.top>=frame.top-.01 && photo.bottom<=frame.bottom+.01)
                }
            }
    }
    @Test fun portraitCinematicIsLargeAndFaceFocusCanCenterTheFace() {
        val frame=StoryLayout.frame(StoryTemplateType.CINEMATIC_16_9)
        val focus=StoryLayout.focusCrop(1200,2000,.5f,.25f)
        val photo=StoryLayout.photo(1200,2000,StoryTemplateType.CINEMATIC_16_9,focus)
        assertEquals(1080f,photo.width,.01f)
        assertEquals((frame.top+frame.bottom)/2,photo.top+photo.height*.25f,.01f)
        for(p in listOf(StoryPhotoPosition(0f,0f),StoryPhotoPosition(1f,1f))) {
            val crop=StoryLayout.photo(1200,2000,StoryTemplateType.CINEMATIC_16_9,p)
            assertTrue(crop.top<=frame.top && crop.bottom>=frame.bottom)
        }
    }
    @Test fun resizingNeverExceedsTheCanvasSafeArea() {
        for(w in listOf(80f,350f,864f,1032f)) for(h in listOf(60f,210f,700f,1470f))
            for(requested in listOf(-1f,.5f,1f,1.8f,2.2f,10f,Float.NaN)) {
                val scale=StoryLayout.textScale(w,h,requested)
                assertTrue(scale>0 && scale<=2.2f)
                assertTrue(w*scale<=1032.01 && h*scale<=1920.01)
            }
    }
    @Test fun extremeDragsClampWholeTextBlockAndDateInsideSafeZone() {
        for(w in listOf(220f,864f,1032f)) for(h in listOf(80f,300f,1130f,1470f))
            for(p in listOf(StoryTextPosition(-10f,-10f),StoryTextPosition(10f,10f),StoryTextPosition(.5f,.5f),
                StoryTextPosition(Float.NaN,Float.POSITIVE_INFINITY))) {
                val b=StoryLayout.block(w,h,p)
                assertTrue(b.left>=24 && b.right<=1056)
                assertTrue(b.top>=0 && b.bottom<=1920)
                assertEquals(w,b.width,.01f);assertEquals(h,b.height,.01f)
                assertEquals(b,StoryLayout.block(w,h,StoryLayout.center(b)))
            }
    }
    @Test fun photoZoomPreservesAspectAndPositionAtBothLimits() {
        for(t in StoryTemplateType.entries) for((w,h) in listOf(4000 to 3000,3000 to 4000)) {
            val base=StoryLayout.photo(w,h,t)
            for(zoom in listOf(.5f,1f,2f,3f,8f)) for(v in listOf(0f,.5f,1f)) {
                val b=StoryLayout.photo(w,h,t,StoryPhotoPosition(v,v,zoom))
                val frame=StoryLayout.frame(t)
                assertEquals(w.toDouble()/h,(b.width/b.height).toDouble(),.0001)
                assertEquals(base.width*zoom.coerceIn(.5f,3f),b.width,.02f)
                assertEquals(frame.left-(b.width-frame.width)*v,b.left,.02f)
            }
        }
    }
    @Test fun centeredPhotoShrinkKeepsOppositeMarginsSymmetric() {
        val frame=StoryLayout.frame(StoryTemplateType.CLEAN_PURE)
        for((w,h) in listOf(3000 to 4000,4000 to 3000,1080 to 1920,1920 to 1080))
            for(zoom in listOf(.5f,.72f,1f)) {
                val p=StoryLayout.photo(w,h,StoryTemplateType.CLEAN_PURE,StoryPhotoPosition(.5f,.5f,zoom))
                assertEquals(p.left-frame.left,frame.right-p.right,.001f)
                assertEquals(p.top-frame.top,frame.bottom-p.bottom,.001f)
            }
    }
    @Test fun cinematicUsesWideFrameAndSideNoteGetsANarrowColumn() {
        val frame=StoryLayout.frame(StoryTemplateType.CINEMATIC_16_9)
        assertEquals(16.0/9,(frame.width/frame.height).toDouble(),.0001)
        assertEquals(16f,StoryLayout.frame(StoryTemplateType.CLEAN_PURE).left,.001f)
        assertTrue(StoryLayout.frame(StoryTemplateType.CLEAN_PURE).height>1800f)
        assertEquals(1080f,StoryLayout.frame(StoryTemplateType.SIDE_JOURNAL).width,.001f)
        assertTrue(StoryLayout.textRegion(StoryTemplateType.SIDE_JOURNAL).left>800f)
    }
}
