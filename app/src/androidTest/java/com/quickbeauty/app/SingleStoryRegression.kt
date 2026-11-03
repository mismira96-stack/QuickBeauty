package com.quickbeauty.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import androidx.core.content.res.ResourcesCompat
import java.io.File

/** Single-canvas interactions; runs only on the dedicated emulator. */
internal object SingleStoryRegression {
    fun run(i: Instrumentation,textStressOnly: Boolean=false): String {
        fun main(block: () -> Unit) {
            var failure: Throwable?=null
            i.runOnMainSync { try { block() } catch(t: Throwable) { failure=t } }
            failure?.let { throw it }
        }
        val context=i.targetContext
        val fixture=File(context.filesDir,"story-fixture.jpg")
        val image=checkNotNull(BeautyBitmapUtils.decodeSampledBitmapFromUri(context,Uri.fromFile(fixture),1920))
        val engine=StoryRenderer(image,ResourcesCompat.getFont(context,R.font.nanum_pen_script)!!,
            ResourcesCompat.getFont(context,R.font.gowun_batang_bold)!!)
        val out=File(context.filesDir,"single-story-review").apply { mkdirs() }
        fun write(bitmap: Bitmap,name: String) {
            File(out,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        check(StoryLayout.availableTemplates==listOf(StoryTemplateType.CLEAN_PURE))
        val captions=if(textStressOnly) listOf("가".repeat(30),"W".repeat(30),"🎉".repeat(30),"👨‍👩‍👧‍👦".repeat(30))
            else listOf("오늘의 나", "가".repeat(30),"한글👨‍👩‍👧‍👦☕")
        for(font in StoryTextStyle.availableFonts) for(vertical in listOf(false,true))
            for(text in captions) for(size in listOf(.5f,2.2f)) {
                val p=engine.captionPlan(StoryTemplateType.CLEAN_PURE,text,font,"2026.10.24",
                    StoryTextPosition(2f,2f),size,verticalText=vertical,dateScale=1.5f)
                check(p.vertical==vertical)
                check(p.layout.lineCount==if(vertical) StoryCharacters.split(text).size else 1)
                check((0 until p.layout.lineCount).all { p.layout.getEllipsisCount(it)==0 })
                check(p.bounds.left>=24 && p.bounds.right<=1056 && p.bounds.top>=0 && p.bounds.bottom<=1920)
                check(kotlin.math.abs(StoryLayout.center(p.fixedDate!!.bounds).x-.5f)<.001f && kotlin.math.abs(p.fixedDate.bounds.bottom-(1920f-StoryLayout.TEXT_BOTTOM_MARGIN))<.01f)
                check(p.fixedDate.paint.textSize==84f)
                if(textStressOnly) {
                    val ink=Bitmap.createBitmap(p.layout.width+32,p.layout.height+32,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(ink);canvas.translate(16f,16f);p.layout.draw(canvas)
                    for(x in 0 until ink.width) check(Color.alpha(ink.getPixel(x,0))==0 && Color.alpha(ink.getPixel(x,ink.height-1))==0) { "Ink clipped vertically" }
                    for(y in 0 until ink.height) check(Color.alpha(ink.getPixel(0,y))==0 && Color.alpha(ink.getPixel(ink.width-1,y))==0) { "Ink clipped horizontally" }
                    ink.recycle()
                }
            }
        if(textStressOnly) { image.recycle();return "30-character Korean, wide Latin, emoji and family grapheme captions fit without wrapping/clipping in 3 fonts, both directions and 2 sizes.\n" }
        write(engine.render(StoryTemplateType.CLEAN_PURE,"좋은하루",textStyle=StoryTextStyle.MEMO,date="2026.10.24"),"single-default.png")
        write(engine.render(StoryTemplateType.CLEAN_PURE,"좋은하루",textStyle=StoryTextStyle.MEMO,date="2026.10.24",
            photoPosition=StoryPhotoPosition(0f,.5f,.72f),verticalText=true,dateScale=1.3f),"single-shrunk-vertical.png")
        val white=engine.captionPlan(StoryTemplateType.CLEAN_PURE,"흰 바탕",StoryTextStyle.MEMO,"2026.10.25",
            captionBackground=StoryBackgroundMode.WHITE,dateBackground=StoryBackgroundMode.NONE,
            datePosition=StoryTextPosition(5f,-5f),dateScale=2.2f)
        check(white.readabilityColor==StoryLayout.CANVAS_COLOR && white.paint.color==Color.BLACK)
        check(white.fixedDate!!.background==Color.TRANSPARENT)
        check(white.fixedDate.bounds.top>=0 && white.fixedDate.bounds.right<=1056)
        val bottom=engine.captionPlan(StoryTemplateType.CLEAN_PURE,"하단 중앙",StoryTextStyle.TEMPLATE,"2026.11.03",
            captionBackground=StoryBackgroundMode.WHITE,dateBackground=StoryBackgroundMode.WHITE)
        check(kotlin.math.abs(StoryLayout.center(bottom.bounds).x-.5f)<.001f)
        check(bottom.bounds.bottom<=bottom.fixedDate!!.bounds.top-StoryLayout.TEXT_STACK_GAP+.01f)
        check(bottom.readabilityColor==StoryLayout.CANVAS_COLOR && bottom.fixedDate!!.background==StoryLayout.CANVAS_COLOR)
        val manual=engine.captionPlan(StoryTemplateType.CLEAN_PURE,"수동 위치",StoryTextStyle.TEMPLATE,"2026.11.03",
            StoryTextPosition(.3f,.35f),datePosition=StoryTextPosition(.7f,.2f))
        check(kotlin.math.abs(StoryLayout.center(manual.bounds).x-.3f)<.001f)
        check(kotlin.math.abs(StoryLayout.center(manual.fixedDate!!.bounds).y-.2f)<.001f)
        val none=engine.captionPlan(StoryTemplateType.CLEAN_PURE,"배경 없음",StoryTextStyle.MEMO,null,captionBackground=StoryBackgroundMode.NONE)
        check(none.readabilityColor==null)
        write(engine.render(StoryTemplateType.CLEAN_PURE,"",photoPosition=StoryPhotoPosition(.5f,.5f,.72f)),"single-centered.png")
        val dark=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val darkRenderer=StoryRenderer(dark)
        val stamp=darkRenderer.captionPlan(StoryTemplateType.CLEAN_PURE,"",StoryTextStyle.TEMPLATE,"2026.10.24",dateBackground=StoryBackgroundMode.WHITE).fixedDate!!
        val rounded=darkRenderer.render(StoryTemplateType.CLEAN_PURE,"",date="2026.10.24",dateBackground=StoryBackgroundMode.WHITE)
        check(rounded.getPixel(stamp.bounds.left.toInt()+1,stamp.bounds.top.toInt()+1)==Color.BLACK) { "Date background still rectangular" }
        check(rounded.getPixel(((stamp.bounds.left+stamp.bounds.right)/2).toInt(),stamp.bounds.top.toInt()+2)==StoryLayout.CANVAS_COLOR)
        rounded.recycle();dark.recycle()

        val monitor=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
        main { context.startActivity(Intent(context,SnsStoryActivity::class.java).apply { data=Uri.fromFile(fixture);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        var activity=checkNotNull(i.waitForMonitorWithTimeout(monitor,15000)) as SnsStoryActivity
        i.removeMonitor(monitor)
        fun field(name: String)=SnsStoryActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        fun editor()=field("editor").get(activity) as StoryEditorView
        fun ready() {
            val end=SystemClock.uptimeMillis()+20000;var done=false
            while(!done && SystemClock.uptimeMillis()<end) {
                main { done=field("renderer").get(activity)!=null && editor().captionInput.captionPlan!=null && activity.hasWindowFocus() }
                if(!done) SystemClock.sleep(100)
            }
            check(done) { "Editor not focused/ready" };i.waitForIdleSync()
        }
        fun tap(x: Float,y: Float) {
            val time=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0);editor().dispatchTouchEvent(e);e.recycle()
            }
        }
        var saved: Uri?=null
        var expectedSaved: Bitmap?=null
        try {
            ready()
            main {
                val row=field("formatRow").get(activity) as android.widget.LinearLayout
                if(row.width>row.childCount*56*context.resources.displayMetrics.density) {
                    val first=row.getChildAt(0);val last=row.getChildAt(row.childCount-1)
                    check(kotlin.math.abs((first.left+last.right)*.5f-row.width*.5f)<2f) { "Font presets are not centered" }
                }
                val buttons=listOf("captionButton","fontButton","sizeButton","verticalButton","today")
                    .map { field(it).get(activity) as android.widget.TextView }
                check(buttons.all { it.gravity==android.view.Gravity.CENTER && !it.includeFontPadding })
                check(buttons.map { it.top+it.height/2 }.distinct().size==1) { "Toolbar controls are not aligned" }
            }
            main {
                check(SnsStoryActivity::class.java.declaredFields.none { it.name=="tabs" })
                val e=editor();e.focusCaption()
                val input=e.captionInput
                val connection=checkNotNull(input.onCreateInputConnection(EditorInfo()))
                connection.setComposingText("오늘",1);connection.finishComposingText();connection.commitText("의 나",1)
                check(input.text.toString()=="오늘의 나")
                (field("today").get(activity) as View).performClick()
                check(e.selectedElement==StoryElement.DATE)
                check(field("dateBackground").get(activity)==StoryBackgroundMode.NONE && field("captionBackground").get(activity)==StoryBackgroundMode.NONE)
                val initial=checkNotNull(e.captionInput.captionPlan)
                check(initial.readabilityColor==null) { "Caption default must not draw a capsule" }
                check(checkNotNull(initial.fixedDate).background==Color.TRANSPARENT) { "Date default must not draw a capsule" }
                e.endEditing()
                val selectedDate=Bitmap.createBitmap(e.width,e.height,Bitmap.Config.ARGB_8888)
                e.draw(Canvas(selectedDate));e.interactionEnabled(false)
                val noSelection=Bitmap.createBitmap(e.width,e.height,Bitmap.Config.ARGB_8888)
                e.draw(Canvas(noSelection))
                check(selectedDate.sameAs(noSelection)) { "Selected date still draws an outline" }
                selectedDate.recycle();noSelection.recycle();e.interactionEnabled(true)

            }
            i.waitForIdleSync();SystemClock.sleep(300)
            i.uiAutomation.takeScreenshot()?.let { write(it,"story-bottom-defaults.png") }
            main {
                val e=editor();val before=e.captionInput.captionPlan!!
                val slider=field("sizeSlider").get(activity) as SeekBar
                check((field("sizePanel").get(activity) as View).visibility==View.VISIBLE)
                val time=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,slider.width*.8f,slider.height/2f,0)
                    slider.dispatchTouchEvent(event);event.recycle()
                }
                val after=e.captionInput.captionPlan!!
                check(after.fixedDate!!.paint.textSize>before.fixedDate!!.paint.textSize)
                check(after.bounds==before.bounds && after.paint.textSize==before.paint.textSize) { "Date resize changed caption" }
                e.selectElement(StoryElement.CAPTION)
                val type=StoryEditorView::class.java
                fun number(name: String)=type.getDeclaredField(name).apply { isAccessible=true }.getFloat(e)
                val b=after.fixedDate.bounds;val scale=number("scale")
                tap(number("originX")+(b.left+b.right)/2*scale,number("originY")+(b.top+b.bottom)/2*scale)
                check(e.selectedElement==StoryElement.DATE) { "Canvas date selection failed" }
                val dsx=number("originX")+(b.left+b.right)/2*scale
                val dsy=number("originY")+(b.top+b.bottom)/2*scale
                val dragTime=SystemClock.uptimeMillis()
                for((action,x,y) in listOf(Triple(MotionEvent.ACTION_DOWN,dsx,dsy),Triple(MotionEvent.ACTION_MOVE,dsx+80f,dsy+90f),Triple(MotionEvent.ACTION_UP,dsx+80f,dsy+90f))) {
                    val event=MotionEvent.obtain(dragTime,SystemClock.uptimeMillis(),action,x,y,0);e.dispatchTouchEvent(event);event.recycle()
                }
                check(field("datePosition").get(activity)!=null) { "Date drag did not update document" }
                check(e.captionInput.captionPlan!!.fixedDate!!.bounds.top>b.top) { "Date did not move down" }

                e.selectElement(StoryElement.CAPTION)
                val dateSize=e.captionInput.captionPlan!!.fixedDate!!.paint.textSize
                e.changeSelectedScale(.8f)
                check(e.captionInput.captionPlan!!.fixedDate!!.paint.textSize==dateSize)
                val anchor=e.currentCaptionPosition!!
                val toggle=field("verticalButton").get(activity) as View
                toggle.performClick();check(e.captionInput.captionPlan!!.vertical)
                check(field("captionPosition").get(activity)==anchor)
                toggle.performClick();check(!e.captionInput.captionPlan!!.vertical)
                val returned=e.currentCaptionPosition!!
                check(kotlin.math.abs(returned.x-anchor.x)<.0001f && kotlin.math.abs(returned.y-anchor.y)<.0001f) { "Direction toggle moved caption" }
                toggle.performClick()
            }
            main {
                @Suppress("UNCHECKED_CAST")
                val formats=field("formatButtons").get(activity) as List<android.widget.TextView>
                check(formats.size==6)
                editor().selectElement(StoryElement.DATE)
                formats[3].performClick()
                check(field("dateStyle").get(activity)==StoryTextStyle.MEMO)
                check(field("dateBackground").get(activity)==StoryBackgroundMode.WHITE)
                check(field("captionBackground").get(activity)==StoryBackgroundMode.NONE)
                formats[0].performClick()
                check(editor().captionInput.captionPlan!!.fixedDate!!.background==Color.TRANSPARENT)
                editor().selectElement(StoryElement.CAPTION)
                formats[3].performClick()
                check(field("textStyle").get(activity)==StoryTextStyle.MEMO)
                check(field("captionBackground").get(activity)==StoryBackgroundMode.WHITE)
                check(field("dateStyle").get(activity)==StoryTextStyle.TEMPLATE)
                check(field("dateBackground").get(activity)==StoryBackgroundMode.NONE)
                editor().endEditing()
            }
            i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { write(it,"inline-format-presets.png") }

            i.waitForIdleSync()
            main {
                val e=editor();e.endEditing()
                val box=e.captionInput
                val sx=box.left+box.width/2f;val sy=box.top+box.height/2f
                val time=SystemClock.uptimeMillis()
                for((action,x,y) in listOf(Triple(MotionEvent.ACTION_DOWN,sx,sy),Triple(MotionEvent.ACTION_MOVE,sx-100f,sy-120f),Triple(MotionEvent.ACTION_UP,sx-100f,sy-120f))) {
                    val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0);e.dispatchTouchEvent(event);event.recycle()
                }
                check(field("captionPosition").get(activity)!=null)
                // Pinch a free photo area, away from the new bottom caption/date positions.
                val cx=e.width*.50f;val cy=e.height*.35f
                var tick=0L
                fun multi(action: Int,span: Float,two: Boolean) {
                    val count=if(two) 2 else 1
                    val props=Array(count) { index -> MotionEvent.PointerProperties().apply { id=index;toolType=MotionEvent.TOOL_TYPE_FINGER } }
                    val coords=Array(count) { index -> MotionEvent.PointerCoords().apply { x=cx+(if(index==0) -span/2 else span/2);y=cy;pressure=1f;size=1f } }
                    val event=MotionEvent.obtain(time,time+tick,action,count,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                    e.dispatchTouchEvent(event);event.recycle();tick+=20
                }
                multi(MotionEvent.ACTION_DOWN,240f,false)
                multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),240f,true)
                for(span in listOf(360f,520f,680f,840f)) multi(MotionEvent.ACTION_MOVE,span,true)
                multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),840f,true)
                multi(MotionEvent.ACTION_UP,840f,false)
                check((field("photoPosition").get(activity) as? StoryPhotoPosition)?.zoom?.let { it>1f }==true) { "Photo pinch did not reach document state" }

                e.selectElement(StoryElement.DATE)
            }
            val recreate=i.addMonitor(SnsStoryActivity::class.java.name,null,false)
            main { activity.recreate() }
            activity=checkNotNull(i.waitForMonitorWithTimeout(recreate,15000)) as SnsStoryActivity
            i.removeMonitor(recreate);ready()
            main {
                val e=editor()
                check(field("verticalText").getBoolean(activity))
                check(field("dateScale").getFloat(activity)>1f && field("textScale").getFloat(activity)==.8f)
                check(e.selectedElement==StoryElement.DATE && field("captionPosition").get(activity)!=null)
                check(field("datePosition").get(activity)!=null)
                check(field("dateBackground").get(activity)==StoryBackgroundMode.NONE && field("captionBackground").get(activity)==StoryBackgroundMode.WHITE)
                check((field("photoPosition").get(activity) as StoryPhotoPosition).zoom>1f)
                val r=field("renderer").get(activity) as StoryRenderer
                val full=r.render(StoryTemplateType.CLEAN_PURE,field("captionValue").get(activity) as String,
                    textStyle=field("textStyle").get(activity) as StoryTextStyle,date=field("storyDate").get(activity) as String,
                    position=field("captionPosition").get(activity) as StoryTextPosition,textScale=.8f,verticalText=true,dateScale=field("dateScale").getFloat(activity),
                    photoPosition=field("photoPosition").get(activity) as StoryPhotoPosition,
                    datePosition=field("datePosition").get(activity) as StoryTextPosition,captionBackground=StoryBackgroundMode.WHITE,dateBackground=StoryBackgroundMode.NONE)
                e.endEditing();e.interactionEnabled(false)
                val actual=Bitmap.createBitmap(e.width,e.height,Bitmap.Config.ARGB_8888);e.draw(Canvas(actual))
                val expected=Bitmap.createBitmap(e.width,e.height,Bitmap.Config.ARGB_8888)
                val scale=minOf(e.width/1080f,e.height/1920f);val ox=(e.width-1080*scale)/2;val oy=(e.height-1920*scale)/2
                Canvas(expected).drawBitmap(full,null,RectF(ox,oy,ox+1080*scale,oy+1920*scale),Paint(Paint.FILTER_BITMAP_FLAG))
                var error=0L;var samples=0
                for(y in 0 until e.height step 4) for(x in 0 until e.width step 4) {
                    val a=actual.getPixel(x,y);val b=expected.getPixel(x,y)
                    error+=kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.green(a)-Color.green(b))+kotlin.math.abs(Color.blue(a)-Color.blue(b));samples+=3
                }
                check(error.toDouble()/samples<7.0) { "Preview/export mismatch" }
                actual.recycle();expected.recycle()
                val ow=e.width;val oh=e.height;val ol=e.left;val ot=e.top
                val rebuild=StoryEditorView::class.java.getDeclaredMethod("rebuild").apply { isAccessible=true }
                try {
                    repeat(2) {
                        e.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY))
                        e.layout(0,0,1080,1920);rebuild.invoke(e)
                    }
                    val exact=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
                    e.draw(Canvas(exact))
                    check(exact.sameAs(full)) { "1:1 preview/export pixels differ" }
                    exact.recycle()
                } finally {
                    e.measure(View.MeasureSpec.makeMeasureSpec(ow,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(oh,View.MeasureSpec.EXACTLY))
                    e.layout(ol,ot,ol+ow,ot+oh);rebuild.invoke(e);e.requestLayout()
                }
                e.interactionEnabled(true)
                saved=SnsStoryActivity::class.java.getDeclaredMethod("saveToGallery",Bitmap::class.java).apply { isAccessible=true }.invoke(activity,full) as Uri
                expectedSaved=full.copy(Bitmap.Config.ARGB_8888,false)
                write(full,"single-current-export.png")
            }
            context.contentResolver.openInputStream(saved!!).use {
                val b=checkNotNull(BitmapFactory.decodeStream(it));check(b.width==1080 && b.height==1920)
                check(b.sameAs(expectedSaved)) { "Saved PNG differs from preview/output pixels" };b.recycle()
            }
            check(context.contentResolver.getType(saved!!)=="image/png")
            SystemClock.sleep(400);i.waitForIdleSync()
            i.uiAutomation.takeScreenshot()?.let { write(it,"single-editor.png") }
            main { editor().focusCaption() }
            val imeDeadline=SystemClock.uptimeMillis()+8000
            var imeReady=false
            while(!imeReady && SystemClock.uptimeMillis()<imeDeadline) {
                main { imeReady=androidx.core.view.ViewCompat.getRootWindowInsets(editor())
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true }
                if(!imeReady) SystemClock.sleep(150)
            }
            check(imeReady) { "Real keyboard did not appear within 8 seconds" }
            i.waitForIdleSync()
            main {
                val e=editor();val p=e.captionInput.captionPlan!!
                val target=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,24f,context.resources.displayMetrics)
                check(p.paint.textSize*e.captionInput.outputScale>=target-.5f) { "Typing preview is too small: ${p.paint.textSize*e.captionInput.outputScale}, target=$target" }
                check(androidx.core.view.ViewCompat.getRootWindowInsets(e)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true) { "Real keyboard missing" }
            }
            i.uiAutomation.takeScreenshot()?.let { write(it,"single-typing.png") }
            return "Single white canvas, horizontal/vertical toggle, 30-character single column, selectable date/caption independent size, pill backgrounds with independent AUTO/WHITE/NONE menus, date and caption drag, photo pinch, real-IME focused preview, recreation, exact 1:1 preview/export pixels, stable direction round-trip, styled font/background sheets, no-background caption/date defaults and lossless saved-PNG pixel round-trip passed. No external sharing.\n"
        } finally { expectedSaved?.recycle();saved?.let { context.contentResolver.delete(it,null,null) };main { activity.finish() };image.recycle() }
    }
}
