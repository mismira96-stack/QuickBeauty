package com.quickbeauty.app

import kotlin.math.min
import kotlin.math.max

enum class StoryTemplateType(val background: Int, val centered: Boolean = false) {
    CLEAN_PURE(0xFFFAF9F7.toInt(),true),
    MUTED_TONE(0xFFEAE6E1.toInt()),
    CINEMATIC_16_9(0xFF141416.toInt()),
    SIDE_JOURNAL(0xFFF7F5F0.toInt(),true),
    FULL_DRIFT(0xFF141416.toInt(),true)
}

data class StoryTextPosition(val x: Float, val y: Float)
data class StoryPhotoPosition(val x: Float, val y: Float, val zoom: Float=1f,val offsetX: Float=0f,val offsetY: Float=0f)

object StoryLayout {
    val availableTemplates=listOf(StoryTemplateType.CLEAN_PURE)
    const val WIDTH=1080f
    const val HEIGHT=1920f
    const val SAFE_TOP=0f
    const val SAFE_BOTTOM=1920f
    const val CANVAS_COLOR=0xFFFAF9F7.toInt()
    const val TEXT_BOTTOM_MARGIN=72f
    const val TEXT_STACK_GAP=24f
    data class Box(val left: Float,val top: Float,val right: Float,val bottom: Float) {
        val width get()=right-left
        val height get()=bottom-top
    }
    fun frame(t: StoryTemplateType)=when(t) {
        StoryTemplateType.CLEAN_PURE -> Box(16f,24f,1064f,1896f)
        StoryTemplateType.MUTED_TONE -> Box(72f,96f,1008f,1824f)
        StoryTemplateType.CINEMATIC_16_9 -> Box(0f,650f,1080f,1257.5f)
        StoryTemplateType.SIDE_JOURNAL -> Box(0f,0f,1080f,1920f)
        StoryTemplateType.FULL_DRIFT -> Box(0f,0f,1080f,1920f)
    }
    fun photo(width: Int,height: Int,t: StoryTemplateType,position: StoryPhotoPosition=StoryPhotoPosition(.5f,.5f)): Box {
        require(width>0 && height>0)
        val a=frame(t)
        val crop=t!=StoryTemplateType.MUTED_TONE
        val s=if(crop) max(a.width/width,a.height/height)
            else min(a.width/width,a.height/height)
        val zoom=if(position.zoom.isFinite()) position.zoom.coerceIn(.5f,3f) else 1f
        val w=width*s*zoom;val h=height*s*zoom
        val x=a.left-(w-a.width)*position.x.coerceIn(0f,1f)+(if(position.offsetX.isFinite()) position.offsetX else 0f)*WIDTH
        val y=a.top-(h-a.height)*position.y.coerceIn(0f,1f)+(if(position.offsetY.isFinite()) position.offsetY else 0f)*HEIGHT
        return Box(x,y,x+w,y+h)
    }
    fun pan(width: Int,height: Int,t: StoryTemplateType,current: StoryPhotoPosition,dx: Float,dy: Float): StoryPhotoPosition {
        val rect=photo(width,height,t,current);val area=frame(t)
        val visibleX=minOf(64f,rect.width);val visibleY=minOf(64f,rect.height)
        val left=(rect.left+dx).coerceIn(area.left+visibleX-rect.width,area.right-visibleX)
        val top=(rect.top+dy).coerceIn(area.top+visibleY-rect.height,area.bottom-visibleY)
        return current.copy(offsetX=current.offsetX+(left-rect.left)/WIDTH,offsetY=current.offsetY+(top-rect.top)/HEIGHT)
    }
    fun textRegion(t: StoryTemplateType)=when(t) {
        StoryTemplateType.CLEAN_PURE -> Box(108f,1330f,972f,1560f)
        StoryTemplateType.MUTED_TONE -> Box(144f,1330f,936f,1560f)
        StoryTemplateType.CINEMATIC_16_9 -> Box(80f,1320f,1000f,1610f)
        StoryTemplateType.SIDE_JOURNAL -> Box(860f,330f,1032f,1550f)
        StoryTemplateType.FULL_DRIFT -> Box(108f,1220f,972f,1540f)
    }
    fun block(width: Float,height: Float,position: StoryTextPosition): Box {
        val w=width.coerceIn(1f,1032f);val h=height.coerceIn(1f,SAFE_BOTTOM-SAFE_TOP)
        val cx=if(position.x.isFinite()) position.x*WIDTH else WIDTH/2
        val cy=if(position.y.isFinite()) position.y*HEIGHT else (SAFE_TOP+SAFE_BOTTOM)/2
        val x=(cx-w/2).coerceIn(24f,WIDTH-24f-w)
        val y=(cy-h/2).coerceIn(SAFE_TOP,SAFE_BOTTOM-h)
        return Box(x,y,x+w,y+h)
    }
    fun center(b: Box)=StoryTextPosition((b.left+b.right)/2/WIDTH,(b.top+b.bottom)/2/HEIGHT)
    fun bottomTextPosition(height: Float, below: Box?=null): StoryTextPosition {
        val bottom=below?.let { it.top-TEXT_STACK_GAP } ?: (HEIGHT-TEXT_BOTTOM_MARGIN)
        return StoryTextPosition(.5f,(bottom-height*.5f)/HEIGHT)
    }
    fun textScale(width: Float,height: Float,requested: Float): Float {
        val finite=if(requested.isFinite()) requested else 1f
        return minOf(finite.coerceIn(.5f,2.2f),1032f/width,1920f/height).coerceAtLeast(.01f)
    }
    fun focusCrop(width: Int,height: Int,focusX: Float,focusY: Float): StoryPhotoPosition {
        val frame=frame(StoryTemplateType.CINEMATIC_16_9)
        val rect=photo(width,height,StoryTemplateType.CINEMATIC_16_9)
        val x=if(focusX.isFinite()) focusX.coerceIn(0f,1f) else .5f
        val y=if(focusY.isFinite()) focusY.coerceIn(0f,1f) else .5f
        return StoryPhotoPosition(
            if(rect.width-frame.width>1f) ((x*rect.width-frame.width/2)/(rect.width-frame.width)).coerceIn(0f,1f) else .5f,
            if(rect.height-frame.height>1f) ((y*rect.height-frame.height/2)/(rect.height-frame.height)).coerceIn(0f,1f) else .5f)
    }
}
